#!/bin/bash
# D1 replay using the installed native car/phone apps; Bash 3.2 (macOS).
# Interface and precedence are documented by --help. No APK install/build,
# permission grants, fake rider, session-end command, or cleanup on interruption.
set -eu
set -o pipefail

usage() {
    cat <<'USAGE'
Usage: scripts/demo.sh [CAR_SERIAL [PHONE_SERIAL [SERVER_URL]]] [options]

Options (override positional arguments, then environment, then defaults):
  --car SERIAL         Car emulator (env CAR_SERIAL; default emulator-5554)
  --phone SERIAL       Phone emulator (env PHONE_SERIAL; default emulator-5556)
  --server-url URL     Host health-check base URL (env SERVER_URL;
                       default http://127.0.0.1:8080)
  --adb PATH           adb executable (env ADB;
                       default $HOME/Library/Android/sdk/platform-tools/adb)
  --car-user USER      current or numeric Android user (env CAR_USER;
                       default current; Pleos commonly uses 10)
  -h, --help           Print this help without contacting server/devices

Phone user is always 0. Examples:
  scripts/demo.sh emulator-5554 emulator-5556
  scripts/demo.sh --car emulator-5554 --phone emulator-5556 --car-user 10
  CAR_SERIAL=emulator-5554 PHONE_SERIAL=emulator-5556 scripts/demo.sh

SERVER_URL only selects curl's /health endpoint. It does not reconfigure either
installed APK: both must already target the same real server (debug APKs normally
use http://10.0.2.2:8080 inside the emulator). No fakeServer/offline preview mode.
Requires running emulators, installed com.maejung / com.maejung.phone, curl,
awk, and interactive stdin. Each Enter records a HUMAN confirmation, not an
automatic state check. Any nonempty reply, EOF, noninteractive stdin, or Ctrl-C
aborts. Device/session state and the last injected GPS fix are retained.

Human checkpoints: gear P + original light state; real app mode + existing
pairing (pair as 민지 only if unpaired), recorded rider name + 민지 once in family;
phone request + precise location permission; car approval + Sharing; fresh
academy fix on both maps; Nearby + light; RR open; RR close; both ended + light
restored. Walking is exactly 15 equal GPS steps, one every 3 seconds (45 seconds).
USAGE
}

die() { printf 'ABORT: %s\n' "$*" >&2; exit 1; }
trap 'printf "\nABORT: interrupted; last GPS and app/session state retained.\n" >&2; exit 130' INT
trap 'printf "\nABORT: terminated; last GPS and app/session state retained.\n" >&2; exit 143' TERM
trap 'printf "\nABORT: terminal closed; last GPS and app/session state retained.\n" >&2; exit 129' HUP

option_car= option_phone= option_server=
adb_bin=${ADB:-"$HOME/Library/Android/sdk/platform-tools/adb"}
car_user=${CAR_USER:-current}
positional_count=0
positional_car= positional_phone= positional_server=
add_positional() {
    positional_count=$((positional_count + 1))
    case "$positional_count" in
        1) positional_car=$1 ;;
        2) positional_phone=$1 ;;
        3) positional_server=$1 ;;
        *) die 'Expected at most CAR_SERIAL PHONE_SERIAL SERVER_URL.' ;;
    esac
}
while [ "$#" -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --car|--phone|--server-url|--adb|--car-user)
            [ "$#" -ge 2 ] && [ -n "$2" ] || die "Missing value for $1 (see --help)."
            case "$2" in --*) die "Missing value for $1 (see --help)." ;; esac
            case "$1" in
                --car) option_car=$2 ;;
                --phone) option_phone=$2 ;;
                --server-url) option_server=$2 ;;
                --adb) adb_bin=$2 ;;
                --car-user) car_user=$2 ;;
            esac
            shift 2 ;;
        --) shift; while [ "$#" -gt 0 ]; do add_positional "$1"; shift; done ;;
        -*) die "Unknown option: $1 (see --help)." ;;
        *) add_positional "$1"; shift ;;
    esac
done
car_serial=${option_car:-${positional_car:-${CAR_SERIAL:-emulator-5554}}}
phone_serial=${option_phone:-${positional_phone:-${PHONE_SERIAL:-emulator-5556}}}
server_url=${option_server:-${positional_server:-${SERVER_URL:-http://127.0.0.1:8080}}}
server_url=${server_url%/}
[ "$car_serial" != "$phone_serial" ] || die 'Car and phone serials must be distinct.'
case "$server_url" in http://?*|https://?*) ;; *) die 'SERVER_URL must start with http:// or https://.' ;; esac
case "$car_user" in current) ;; ''|*[!0-9]*) die '--car-user must be current or a numeric user ID.' ;; esac
command -v curl >/dev/null 2>&1 || die 'curl is required.'
command -v awk >/dev/null 2>&1 || die 'awk is required.'
command -v "$adb_bin" >/dev/null 2>&1 || die "adb not executable: $adb_bin; set ADB or --adb."
repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)

server_failure() {
    printf 'ABORT: %s/health: %s; require HTTP 200 and JSON status "ok".\n' "$server_url" "$1" >&2
    printf 'No adb commands have run. Start the local server in another terminal:\n' >&2
    printf '  cd %q\n  ./gradlew bootRun\n' "$repo_dir/server" >&2
    printf 'Then retry with --server-url http://127.0.0.1:8080 (or your running server).\n' >&2
    exit 1
}

# Health is deliberately before EVERY adb call, including read-only preflight.
printf 'Checking %s/health before touching devices...\n' "$server_url"
health_reply=$(curl --silent --show-error --connect-timeout 3 --max-time 10 \
    --write-out '\n%{http_code}' "$server_url/health") || server_failure 'curl failed'
health_code=${health_reply##*$'\n'}
health_body=${health_reply%$'\n'*}
[ "$health_code" = 200 ] || server_failure "HTTP $health_code"
printf '%s\n' "$health_body" | awk '
    { body = body $0 }
    END { exit !(body ~ /"status"[[:space:]]*:[[:space:]]*"ok"/) }
' || server_failure 'missing status "ok"'
printf 'Health passed (HTTP 200, status ok).\n'
[ -t 0 ] || die 'Interactive stdin required; no device commands run. Use a terminal, not a pipe or redirected input.'

adb_checked() {
    local output
    output=$("$adb_bin" "$@" 2>&1) || die "adb $*: $output"
    case "$output" in
        *'Error:'*|*'error:'*|*'Exception'*|*'KO:'*) die "adb $*: $output" ;;
    esac
    printf '%s\n' "$output"
}

check_device() {
    local device_state
    device_state=$(adb_checked -s "$1" get-state) || exit 1
    [ "$device_state" = device ] || die "$2 serial $1 is not available in device state: $device_state"
}
check_package() {
    local package_path activity
    package_path=$(adb_checked -s "$1" shell pm path --user "$2" "$3") || exit 1
    case "$package_path" in package:*.apk*) ;; *) die "$3 is not installed for user $2 on $1: $package_path" ;; esac
    activity=$(adb_checked -s "$1" shell cmd package resolve-activity --brief --user "$2" \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$3") || exit 1
    case "$activity" in
        *"$3/.MainActivity"*|*"$3/$3.MainActivity"*) ;;
        *) die "Expected native $3/.MainActivity launcher on $1, got: $activity" ;;
    esac
}
launch() {
    local result
    result=$(adb_checked -s "$1" shell am start -W --user "$2" -n "$3/.MainActivity") || exit 1
    case "$result" in *'Status: ok'*) ;; *) die "Launch did not report Status: ok on $1: $result" ;; esac
    printf '%s\n' "$result"
}
confirm() {
    local reply
    printf '\nHUMAN CHECK — %s\n' "$1"
    printf 'Only when confirmed, press Enter (any text / Ctrl-C aborts): '
    IFS= read -r reply || die 'EOF while waiting for human confirmation; last GPS and app/session state retained.'
    [ -z "$reply" ] || die 'Human confirmation declined; last GPS and app/session state retained.'
    printf 'Human confirmation recorded; app state was not automatically verified.\n'
}
geo_fix() {
    local result
    result=$(adb_checked -s "$1" emu geo fix "$2" "$3") || exit 1
    case "$result" in *OK*) ;; *) die "GPS injection not acknowledged on $1: $result" ;; esac
}

check_device "$car_serial" car
check_device "$phone_serial" phone
resolved_car_user=$car_user
if [ "$car_user" = current ]; then
    resolved_car_user=$(adb_checked -s "$car_serial" shell am get-current-user) || exit 1
fi
case "$resolved_car_user" in ''|*[!0-9]*) die "Could not resolve car user: $resolved_car_user" ;; esac
# Check BOTH installations before launching either app. Fake transport / preview
# have the same package IDs: their absence must be confirmed on the home screens.
check_package "$car_serial" "$resolved_car_user" com.maejung
check_package "$phone_serial" 0 com.maejung.phone
printf 'Native launchers found: car %s (user %s -> %s), phone %s (user 0).\n' \
    "$car_serial" "$car_user" "$resolved_car_user" "$phone_serial"
launch "$car_serial" "$car_user" com.maejung
launch "$phone_serial" 0 com.maejung.phone

printf '\nD1: 수요일 밤 10시, 대치동 학원가 — 민지와 엄마 차\n'
printf 'Virtual Console setup, if needed, in another terminal:\n  %q -s %q forward tcp:8088 tcp:8088\n' "$adb_bin" "$car_serial"
printf 'Open http://127.0.0.1:8088/ (browser width >=1600px for BODY/RR).\n'
confirm 'Virtual Console에서 기어를 P로 두고 RR 문이 닫혔는지 확인하세요. 기어 P를 끝까지 유지하세요. 실내등의 원래 상태(OFF/ON/AUTOMATIC)를 기록하세요. 콘솔에는 실내등이 표시되지 않으므로 차 앱/차량 조회 로그를 확인하세요.'

geo_fix "$car_serial" 127.06030 37.49950
geo_fix "$phone_serial" 127.06280 37.49830
printf '\n차 GPS: 127.06030 37.49950; 학원 GPS: 127.06280 37.49830 (경도 위도).\n'
printf '차 위치 권한은 정확한 위치/앱 사용 중 허용, 위치(GPS)는 양쪽 기기에서 켜 두세요.\n'
confirm '양쪽 네이티브 마중 홈을 보세요. 차에 "서버 연결됨"이 보이고 "가짜 요청 받기 (디버그)"가 없어야 합니다. 있으면 즉시 Ctrl-C: fakeServer APK로는 진행하지 마세요. 폰은 오프라인 미리보기를 나와 실제 연결 화면이어야 합니다. 기존 연결은 이름이 MinjiP3 등이어도 그대로 사용 가능하며, 삭제·초기화·이름 변경 없이 테스트 데이터를 유지하세요. 시연의 탑승자 표시 이름을 기록하세요. 미연결일 때만 차 "가족 연결" → 6자리 코드 → 폰 이름 "민지"와 코드 입력 → "연결하기". 차 가족 목록에 민지가 정확히 한 번 있고 폰 실제 홈에 연결됨이 보일 때만 Enter. 다른 이름의 기존 가족 항목은 그대로 유지하세요.'

confirm '폰에서 실제 "데리러 와줘"를 한 번 누르세요. 위치 권한 창에서 "정확한 위치(Precise)"와 "앱 사용 중에만 허용(While using the app)"을 선택하고 알림 권한도 허용하세요. 이미 거부했으면 Android 설정 → 앱 → 마중 → 권한 → 위치에서 정확한 위치/앱 사용 중 허용을 설정한 뒤 앱으로 돌아오세요. 폰의 응답 대기와 차의 탑승자 요청이 보이고, 요청 이름이 기록한 표시 이름(민지/저장된 테스트 이름)과 같은지 확인한 뒤 Enter. 아직 차에서 승인하지 마세요.'
confirm '차에서 기록한 표시 이름의 탑승자 요청 "수락" 버튼을 직접 누르세요(음성 승인 검증 아님). 폰을 화면 앞에 둔 채 양쪽이 공유 중(SHARING/엄마가 데리러 와요) 화면이 될 때까지 기다린 뒤 Enter. 승인·공유 전이를 스크립트가 검사하지는 않습니다.'

# startSharing subscribes only after approval; an earlier academy fix may be stale.
geo_fix "$car_serial" 127.06030 37.49950
geo_fix "$phone_serial" 127.06280 37.49830
confirm '공유 시작 후 학원 GPS를 새로 주입했습니다. 폰 위치 서비스가 첫 fix를 받고 양쪽 지도에 차/탑승자가 표시되며 약 258m 거리인 것을 확인한 뒤 Enter. 위치가 없거나 권한 경고가 있으면 이동을 시작하지 말고 중단하세요.'

printf '\n학원 → 차: 15등분, 각 이동 전 3초 대기, 총 대기 45초.\n'
step=1
while [ "$step" -le 15 ]; do
    sleep 3 || die 'Walking wait interrupted; last GPS retained.'
    coordinates=$(awk -v step="$step" 'BEGIN {
        t = step / 15
        printf "%.8f %.8f", 127.06280 + (127.06030 - 127.06280) * t,
            37.49830 + (37.49950 - 37.49830) * t
    }') || die "Coordinate interpolation failed at step $step."
    longitude=${coordinates%% *}
    latitude=${coordinates#* }
    geo_fix "$phone_serial" "$longitude" "$latitude"
    printf 'GPS acknowledged %02d/15: %s %s (앱 수신 여부는 수동 확인)\n' "$step" "$longitude" "$latitude"
    step=$((step + 1))
done
printf '마지막 GPS를 차 위치에 유지합니다. 되돌리거나 세션을 끝내지 않습니다.\n'
confirm '아직 RR 문을 열지 마세요. 차 Nearby/근접 상태와 30m 이내, 실내등 ON 및 폰 "실내등을 켰어요" + "실내등 켜진 차를 찾아주세요" 안내가 모두 보일 때까지 기다리세요. 실제 실내등은 차량 조회 로그/차량 신호로 확인하세요(콘솔에는 표시 안 됨). 자동 확인은 없으며 전부 확인한 뒤 Enter.'
confirm '기어 P와 Nearby를 유지한 채 Virtual Console BODY 패널에서 뒷문 RR을 여세요. 열린 상태를 확인하고 문을 열어 둔 채 Enter.'
confirm '이제 Virtual Console BODY 패널에서 RR 문을 닫으세요. 닫힌 상태를 확인한 뒤 Enter.'
confirm '양쪽 앱의 탑승 확인 종료(boarded)를 기다리고 직접 확인하세요(차 종료 화면은 잠깐 뒤 홈으로 돌아갑니다). 폰 위치 공유 ongoing 알림이 사라지고, 실내등이 처음 기록한 상태로 복구됐는지도 차량 조회 로그/차량 신호로 확인하세요. 모두 확인한 뒤에만 Enter; 확인하지 못하면 Ctrl-C.'
printf '\nD1 replay finished with HUMAN confirmations of Nearby/light, boarding, both ends, and light restoration.\n'
printf 'Only health, device/package/launcher checks, launches, and GPS acknowledgements were checked automatically.\n'

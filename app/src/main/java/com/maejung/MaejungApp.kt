package com.maejung

import android.app.Application

/**
 * 연결과 세션을 들고 있는 곳. 화면 회전·Activity 재생성에 끊기지 않게 Application 범위에 둔다.
 * CarSessionManager(A2·A5)가 생기면 여기서 만든다. DI 프레임워크는 쓰지 않는다
 */
class MaejungApp : Application()

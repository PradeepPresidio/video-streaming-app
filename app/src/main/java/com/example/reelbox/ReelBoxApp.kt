package com.example.reelbox

import android.app.Application

class ReelBoxApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

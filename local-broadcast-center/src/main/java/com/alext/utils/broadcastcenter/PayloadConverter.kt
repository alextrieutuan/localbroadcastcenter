package com.alext.utils.broadcastcenter

import android.os.Bundle

interface PayloadConverter<T> {

    fun convert(bundle: Bundle): T
}

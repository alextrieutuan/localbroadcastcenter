package com.alext.utils.broadcastcenter

import android.os.Bundle
import android.os.Parcelable
import com.google.gson.Gson
import kotlinx.android.parcel.Parcelize
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.Serializable

@RunWith(RobolectricTestRunner::class)
class PayloadTest {

    @Test
    fun asBundle() {
        val payloadString = Payload("String")
        payloadString.asBundle().getString(Payload.KEY_PAYLOAD) should_be "String"

        val payloadInt = Payload(1)
        payloadInt.asBundle().getInt(Payload.KEY_PAYLOAD) should_be 1

        val payloadFloat = Payload(1.0f)
        payloadFloat.asBundle().getFloat(Payload.KEY_PAYLOAD) should_be 1.0f

        val payloadLong = Payload(1L)
        payloadLong.asBundle().getLong(Payload.KEY_PAYLOAD) should_be 1L

        val payloadDouble= Payload(1.0)
        payloadDouble.asBundle().getDouble(Payload.KEY_PAYLOAD) should_be 1.0

        val payloadBoolean = Payload(true)
        payloadBoolean.asBundle().getBoolean(Payload.KEY_PAYLOAD) should_be true

        val byteValue = 1.0f.toByte()
        val payloadByte= Payload(byteValue)
        payloadByte.asBundle().getByte(Payload.KEY_PAYLOAD) should_be byteValue

        val payloadChar = Payload('A')
        payloadChar.asBundle().getChar(Payload.KEY_PAYLOAD) should_be 'A'

        val payloadParcelable = Payload(SampleParcelable("abcd"))
        payloadParcelable.asBundle().getParcelable<SampleParcelable>(Payload.KEY_PAYLOAD)?.content should_be "abcd"

        val payloadSerializable= Payload(SampleSerializable("abcd"))
        (payloadSerializable.asBundle().getSerializable(Payload.KEY_PAYLOAD) as SampleSerializable).content should_be "abcd"

        val bundle = Bundle()
        bundle.putString("AKey", "AValue")
        val payloadBundle = Payload(bundle)
        payloadBundle.asBundle().getBundle(Payload.KEY_PAYLOAD)?.getString("AKey") should_be "AValue"

        val gson = Gson()
        val sampleObj = SampleObject("abcd")
        val payloadOther= Payload(sampleObj)
        val bundleContent = payloadOther.asBundle()
        bundleContent.getBoolean(Payload.KEY_AS_JSON) should_be true
        val jsonContent = bundleContent.getString(Payload.KEY_PAYLOAD)
        @Suppress("UNCHECKED_CAST")
        val classType = bundleContent.getSerializable(Payload.KEY_CLASS_TYPE) as Class<SampleObject>
        val deserializeObj = gson.fromJson(jsonContent, classType)
        deserializeObj.content should_be "abcd"
    }

    @Parcelize
    private class SampleParcelable(val content: String) : Parcelable

    private class SampleSerializable(val content: String) : Serializable

    private class SampleObject(val content: String)
}

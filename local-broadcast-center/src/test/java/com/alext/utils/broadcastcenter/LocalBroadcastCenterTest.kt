package com.alext.utils.broadcastcenter

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalBroadcastCenterTest {

    private val context = mockk<Application>()
    private val broadcastManager: LocalBroadcastManager = mockk {
        every { sendBroadcast(any()) } returns true
        every { registerReceiver(any(), any()) } just Runs
        every { unregisterReceiver(any()) } just Runs
    }

    private lateinit var activityCallback: Application.ActivityLifecycleCallbacks

    @Before
    fun setUp() {
        mockkStatic(LocalBroadcastManager::class)
        every { LocalBroadcastManager.getInstance(context) } returns broadcastManager
        every { context.applicationContext } returns context

        val slot = slot<Application.ActivityLifecycleCallbacks>()
        every { context.registerActivityLifecycleCallbacks(capture(slot)) }.answers {
            activityCallback = slot.captured
        }
        LocalBroadcastCenter.init(context)
    }

    private val sampleAction = "SampleAction"

    @Test
    fun broadcast() {
        LocalBroadcastCenter.broadcast(sampleAction)

        verify {
            broadcastManager.sendBroadcast(withArg {
                (this.actual as Intent).action should_be sampleAction
            })
        }
    }

    @Test
    fun broadcastWithPayload() {
        LocalBroadcastCenter.broadcast(sampleAction, Payload(1))

        verify {
            broadcastManager.sendBroadcast(withArg {
                val intent = (this.actual as Intent)
                val action = intent.action
                val bundle = intent.extras

                action should_be sampleAction
                bundle should_not_be null
            })
        }
    }

    @Test
    fun register() {
        val onReceived = mockk<() -> Unit>()
        every { onReceived.invoke() } just Runs

        LocalBroadcastCenter.register(this, sampleAction, onReceived)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        slotReceiver.captured.onReceive(context, Intent())
        verify { onReceived.invoke() }
    }

    @Test
    fun registerWithPayload() {
        val onReceived = mockk<(Int) -> Unit>()
        every { onReceived.invoke(any()) } just Runs

        LocalBroadcastCenter.register(this, sampleAction, onReceived)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        val payload = Payload(5)
        val bundle = payload.asBundle()

        slotReceiver.captured.onReceive(context, Intent(sampleAction).apply {
            putExtras(bundle)
        })

        verify { onReceived.invoke(5) }
    }

    @Test
    fun registerWithPayloadJson() {
        val onReceived = mockk<(SampleObject) -> Unit>()
        every { onReceived.invoke(any()) } just Runs

        LocalBroadcastCenter.register(this, sampleAction, onReceived)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        val payload = Payload(SampleObject("TuanDepZai"))
        val bundle = payload.asBundle()

        slotReceiver.captured.onReceive(context, Intent(sampleAction).apply {
            putExtras(bundle)
        })

        verify {
            onReceived.invoke(withArg {
                (this.actual as SampleObject).content should_be "TuanDepZai"
            })
        }
    }

    @Test
    fun registerWithConverter() {
        val mockConverter = mockk<PayloadConverter<SampleObject>> {
            every { convert(any()) } returns SampleObject("TuanDepZai")
        }
        val onReceived = mockk<(SampleObject) -> Unit>()
        every { onReceived.invoke(any()) } just Runs
        LocalBroadcastCenter.register(this, sampleAction, mockConverter, onReceived)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        slotReceiver.captured.onReceive(context, Intent(sampleAction).apply {
            putExtras(Bundle())
        })

        verify {
            onReceived.invoke(withArg {
                (this.actual as SampleObject).content should_be "TuanDepZai"
            })
        }
    }

    @Test
    fun registerGeneralPackage() {
        val onReceived = mockk<(Intent) -> Unit>()
        every { onReceived.invoke(any()) } just Runs
        LocalBroadcastCenter.registerForIntent(this, sampleAction, onReceived)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        slotReceiver.captured.onReceive(context, Intent(sampleAction).apply {
            putExtra("AKey", "AValue")
        })

        verify {
            onReceived.invoke(withArg {
                val intent = this.actual as Intent
                intent.action == sampleAction && intent.extras?.get("AKEY") == "AValue"
            })
        }
    }

    @Test
    fun unregisterAnUnregistered() {
        val onReceived1 = mockk<(Int) -> Unit>()

        LocalBroadcastCenter.register(this, sampleAction, onReceived1)

        val slotReceiver = slot<BroadcastReceiver>()
        verify {
            broadcastManager.registerReceiver(capture(slotReceiver), withArg {
                val intentFilter = this.actual as IntentFilter
                intentFilter.getAction(0) should_be sampleAction
            })
        }

        every { broadcastManager.unregisterReceiver(slotReceiver.captured) } throws IllegalArgumentException()

        LocalBroadcastCenter.unregister(onReceived1)
    }

    @Test
    fun unregisterForAction() {
        val onReceived = mockk<(Int) -> Unit>()
        val where = Any()

        LocalBroadcastCenter.register(where, "action1", onReceived)
        LocalBroadcastCenter.register(where, "action1", onReceived)
        LocalBroadcastCenter.register(where, "action2", onReceived)
        LocalBroadcastCenter.register(where, "action3", onReceived)

        LocalBroadcastCenter.unregister("action1")

        LocalBroadcastCenter.actionMap.size should_be 2
        LocalBroadcastCenter.registerLocationMap[where]?.size should_be 2
        LocalBroadcastCenter.callbackMap[onReceived.hashCode()]?.size should_be 2
    }

    @Test
    fun unregisterForLocation() {
        val onReceived = mockk<(Int) -> Unit>()
        val where = Any()
        val anotherWhere = Any()

        LocalBroadcastCenter.register(where, "action", onReceived)
        LocalBroadcastCenter.register(where, "action2", onReceived)
        LocalBroadcastCenter.register(anotherWhere, "action3", onReceived)

        LocalBroadcastCenter.unregister(where)

        LocalBroadcastCenter.registerLocationMap.size should_be 1
        LocalBroadcastCenter.actionMap.size should_be 1
        LocalBroadcastCenter.callbackMap.size should_be 1
    }

    @Test
    fun unregisterForCallback() {
        val onReceived = mockk<(Int) -> Unit>()
        val whereToRegister = Any()
        val anotherPlaceToRegister = Any()

        LocalBroadcastCenter.register(whereToRegister, sampleAction, onReceived)
        LocalBroadcastCenter.register(anotherPlaceToRegister, sampleAction, onReceived)

        LocalBroadcastCenter.unregister(onReceived)

        LocalBroadcastCenter.registerLocationMap.size should_be 0
        LocalBroadcastCenter.actionMap.size should_be 0
        LocalBroadcastCenter.callbackMap.size should_be 0
    }

    @Test
    fun unregisterForCallbackVoid() {
        val onReceived = mockk<() -> Unit>()
        val whereToRegister = Any()
        val anotherPlaceToRegister = Any()

        LocalBroadcastCenter.register(whereToRegister, sampleAction, onReceived)
        LocalBroadcastCenter.register(anotherPlaceToRegister, sampleAction, onReceived)

        LocalBroadcastCenter.unregister(onReceived)

        LocalBroadcastCenter.registerLocationMap.size should_be 0
        LocalBroadcastCenter.actionMap.size should_be 0
        LocalBroadcastCenter.callbackMap.size should_be 0
    }

    @Test
    fun unregisterWhenActivityDestroyed() {
        val onReceived = mockk<() -> Unit>()
        val activity = mockk<Activity>()
        LocalBroadcastCenter.register(activity, sampleAction, onReceived)

        activityCallback.onActivityDestroyed(activity)

        LocalBroadcastCenter.registerLocationMap.size should_be 0
        LocalBroadcastCenter.actionMap.size should_be 0
        LocalBroadcastCenter.callbackMap.size should_be 0
    }

    @Test
    fun unregisterWhenFragmentDestroyed() {
        val onReceived = mockk<() -> Unit>()
        val fragment = mockk<Fragment>()
        val activity = mockk<FragmentActivity>()

        LocalBroadcastCenter.register(fragment, sampleAction, onReceived)

        val supportFragmentManager = mockk<FragmentManager>()
        every { activity.supportFragmentManager } returns supportFragmentManager

        val slotCb = slot<FragmentManager.FragmentLifecycleCallbacks>()
        every { supportFragmentManager.registerFragmentLifecycleCallbacks(capture(slotCb), any()) }.answers {
            slotCb.captured.onFragmentViewDestroyed(supportFragmentManager, fragment)
        }

        activityCallback.onActivityCreated(activity, null)

        LocalBroadcastCenter.registerLocationMap.size should_be 0
        LocalBroadcastCenter.actionMap.size should_be 0
        LocalBroadcastCenter.callbackMap.size should_be 0
    }

    @After
    fun tearDown() {
        LocalBroadcastCenter.teardown()
    }

    private data class SampleObject(val content: String)
}

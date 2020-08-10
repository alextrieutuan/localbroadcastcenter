package com.alext.utils.broadcastcenter

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.alext.utils.broadcastcenter.Payload.Companion.KEY_AS_JSON
import com.alext.utils.broadcastcenter.Payload.Companion.KEY_CLASS_TYPE
import com.alext.utils.broadcastcenter.Payload.Companion.KEY_PAYLOAD
import com.google.gson.Gson
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * A wrapper on top of [LocalBroadcastManager] to wrap broadcasting/registering [Intent] package through [LocalBroadcastManager]
 * then exposes to user a simple, clean apis [LocalBroadcastCenter]
 *
 * Instead of prepare [Intent], put [Bundle] to [Intent] then broadcast them by using [LocalBroadcastManager.sendBroadcast]
 * You could simply use [LocalBroadcastCenter.broadcast]
 *
 * And instead of register to a [BroadcastReceiver] then parsing [Intent] to get [Bundle] then the associated object
 * You could simply use [LocalBroadcastCenter.register]
 *
 * And for unregister, you could simply use [LocalBroadcastCenter.unregister]
 *
 * If receiver registered in activity or fragment, those receivers will be unregistered once activity/fragment destroyed
 */
object LocalBroadcastCenter : Application.ActivityLifecycleCallbacks {

    private var initialized = false

    private const val TAG = "LocalBroadcastCenter"

    /**
     * Core Android [LocalBroadcastManager]
     */
    private lateinit var broadcastManager: LocalBroadcastManager

    /**
     * For dealing with generic json content
     */
    private lateinit var gson: Gson

    /**
     * Store the mapping of received callback and set of [BroadcastReceiver]s
     */
    @VisibleForTesting
    val callbackMap: HashMap<Int, HashSet<BroadcastReceiver>> = HashMap()

    /**
     * Store the mapping of where those receivers registered and theme
     */
    @VisibleForTesting
    val registerLocationMap: WeakHashMap<Any, HashSet<BroadcastReceiver>> = WeakHashMap()

    /**
     * Store the mapping of action name and a set [BroadcastReceiver]s
     */
    @VisibleForTesting
    val actionMap: HashMap<String, HashSet<BroadcastReceiver>> = HashMap()

    /**
     * The handler to broadcast on main thread
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The lock object to make code sync
     */
    private val LOCK = Any()

    /**
     * Use to init this [LocalBroadcastCenter]
     *
     * Will throws [IllegalArgumentException] if calling any public function without init first
     */
    fun init(context: Context) {
        if (initialized) {
            Log.d(TAG,"Already initialized")
            return
        }
        synchronized(LOCK) {
            broadcastManager = LocalBroadcastManager.getInstance(context)
            gson = Gson()
            (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
            initialized = true
        }
    }

    /**
     * Call this function to broadcast an action with attached [payload]
     *
     * @param action The action to identifier broadcast package
     * @param payload The attached payload
     */
    fun <T : Any> broadcast(action: String, payload: Payload<T>) {
        initializedBlock {
            val intent = Intent(action)
            intent.putExtras(payload.asBundle())

            mainHandler.post { broadcastManager.sendBroadcast(intent) }
        }
    }

    /**
     * Broadcast an action without any [Payload]
     *
     * @param action The action to identify broadcast package
     */
    fun broadcast(action: String) {
        initializedBlock {
            mainHandler.post { broadcastManager.sendBroadcast(Intent(action)) }
        }
    }

    /**
     * Register to receiver any broadcast package with [action]
     *
     * @param where Where is this broadcast receiver be registered
     * @param action The identifier of broadcast package
     * @param onReceived with generic type [T] will be invoked if new package received
     */
    fun <T : Any> register(where: Any, action: String, onReceived: (T) -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, data: Intent) {
                        val payload = data.extras?.extractPayloadObject<T>()
                        if (payload == null) {
                            Log.e(TAG, "Can't parse or payload is missing")
                        } else {
                            onReceived.invoke(payload)
                        }
                    }
                }

                registerAndStore(where, action, onReceived.hashCode(), receiver)
            }
        }
    }

    /**
     * Register to receiver any broadcast package with [action]
     *
     * @param where Where is this broadcast receiver be registered
     * @param action The identifier of broadcast package
     * @param onReceived Will be invoked if new package received
     */
    fun register(where: Any, action: String, onReceived: () -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, data: Intent) {
                        onReceived.invoke()
                    }
                }

                registerAndStore(where, action, onReceived.hashCode(), receiver)
            }
        }
    }

    /**
     * Register to receiver any broadcast package with [action] with a [PayloadConverter] to convert [Bundle]
     * to [Payload] with type [T]
     *
     * @param where Where is this broadcast receiver be registered
     * @param action The identifier of broadcast package
     * @param converter The converter to convert [Bundle] to [T]
     * @param onReceived with generic type [T] will be invoked if new package received
     */
    fun <T> register(where: Any, action: String, converter: PayloadConverter<T>, onReceived: (T) -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, data: Intent) {
                        data.extras?.let {
                            onReceived.invoke(converter.convert(it))
                        }
                    }
                }

                registerAndStore(where, action, onReceived.hashCode(), receiver)
            }
        }
    }

    /**
     * Register to receiver any broadcast package with [action] but not specific any type of Payload to receive,
     * just a general [Intent] package
     *
     * Use on the case you want to listen to any Broadcast Intent whatever it came from
     *
     * @param where Where is this broadcast receiver be registered
     * @param action The identifier of broadcast package
     * @param onReceived with type [Intent] will be invoked if new package received
     */
    fun registerForIntent(where: Any, action: String, onReceived: (Intent) -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, data: Intent) {
                        onReceived.invoke(data)
                    }
                }

                registerAndStore(where, action, onReceived.hashCode(), receiver)
            }
        }
    }

    private fun registerAndStore(where: Any, action: String, callbackHashCode: Int, receiver: BroadcastReceiver) {
        broadcastManager.registerReceiver(receiver, IntentFilter(action))

        fun <K> setOrPutReceiver(key: K, receiver: BroadcastReceiver, map: MutableMap<K, HashSet<BroadcastReceiver>>) {
            val setOfReceiver = map[key]
            if (setOfReceiver == null) {
                val newSet = HashSet<BroadcastReceiver>()
                newSet.add(receiver)
                map[key] = newSet
            } else {
                setOfReceiver.add(receiver)
            }
        }

        setOrPutReceiver(callbackHashCode, receiver, callbackMap)
        setOrPutReceiver(where, receiver, registerLocationMap)
        setOrPutReceiver(action, receiver, actionMap)
    }

    /**
     * Unregister all [BroadcastReceiver]s which be registered at [where]
     */
    fun unregister(where: Any) {
        initializedBlock {
            synchronized(LOCK) {
                val receivers = registerLocationMap.remove(where)
                if (receivers != null) {
                    receivers.forEach { it.silentlyUnregister() }

                    removeFromMap(receivers, actionMap)
                    removeFromMap(receivers, callbackMap)
                }
            }
        }
    }

    /**
     * Unregister all [BroadcastReceiver]s which be registered with [action]
     */
    fun unregister(action: String) {
        initializedBlock {
            synchronized(LOCK) {
                val receivers = actionMap.remove(action)
                if (receivers != null) {
                    receivers.forEach { it.silentlyUnregister() }

                    removeFromMap(receivers, registerLocationMap)
                    removeFromMap(receivers, callbackMap)
                }
            }
        }
    }

    /**
     * Unregister all [BroadcastReceiver]s which come as a pair with [onReceived]
     */
    fun unregister(onReceived: () -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                removeCallback(onReceived.hashCode())
            }
        }
    }

    /**
     * Unregister a [BroadcastReceiver] which come as a pair with [onReceived]
     */
    fun <T : Any> unregister(onReceived: (T) -> Unit) {
        initializedBlock {
            synchronized(LOCK) {
                removeCallback(onReceived.hashCode())
            }
        }
    }

    private fun removeCallback(hashCode: Int) {
        val receivers = callbackMap.remove(hashCode)
        if (receivers != null) {
            receivers.forEach { it.silentlyUnregister() }

            removeFromMap(receivers, registerLocationMap)
            removeFromMap(receivers, actionMap)
        }
    }

    private fun <K> removeFromMap(receivers: HashSet<BroadcastReceiver>, map: MutableMap<K, HashSet<BroadcastReceiver>>) {
        val itr = map.entries.iterator() as MutableIterator<Map.Entry<K, HashSet<BroadcastReceiver>>>
        while (itr.hasNext()) {
            val entry = itr.next()
            val value = entry.value
            val intersect = receivers.intersect(value)

            val shouldRemoveKey = intersect.size == value.size
            if (shouldRemoveKey) {
                itr.remove()
            } else {
                value.removeAll(intersect)
            }
        }
    }

    /**
     * Convenient function to silently unregister a [BroadcastReceiver]
     *
     * Will swallow [IllegalArgumentException] in case this broadcast was already unregistered
     */
    private fun BroadcastReceiver.silentlyUnregister() {
        try {
            broadcastManager.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Was already unregistered?")
        }
    }

    @VisibleForTesting
    fun teardown() {
        initializedBlock {
            callbackMap.forEachValue { receivers ->
                receivers.forEach { it.silentlyUnregister() }
            }
            callbackMap.clear()

            registerLocationMap.forEachValue { receivers ->
                receivers.forEach { it.silentlyUnregister() }
            }
            registerLocationMap.clear()

            actionMap.forEachValue { receivers ->
                receivers.forEach { it.silentlyUnregister() }
            }
            actionMap.clear()

            initialized = false
        }
    }

    /**
     * Extract a attached payload object with type [T] from a [Bundle]
     *
     * @return nullable type T of payload object
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> Bundle.extractPayloadObject(): T? {
        val isJson = getBoolean(KEY_AS_JSON, false)
        return if (isJson) {
            val content = getString(KEY_PAYLOAD)
            val classType = getSerializable(KEY_CLASS_TYPE) as Class<T>
            return gson.fromJson(content, classType)
        } else {
            get(KEY_PAYLOAD) as? T
        }
    }

    /**
     * A block that will check for the initialization of [LocalBroadcastCenter]
     *
     * Client should call [init] first in order to use all public functions
     */
    private inline fun initializedBlock(block: () -> Unit) {
        require(initialized) { "Call init first" }
        block()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewDestroyed(fm: FragmentManager, fragment: Fragment) {
                    unregisterIfSame(fragment)
                }
            }, true)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        unregisterIfSame(activity)
    }

    private fun unregisterIfSame(objectToCompare: Any) {
        val keys = registerLocationMap.keys.filter { it == objectToCompare }
        keys.forEach { unregister(it) }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityStopped(activity: Activity) {}
}

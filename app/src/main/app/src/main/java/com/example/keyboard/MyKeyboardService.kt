package com.example.aikeyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** الأوامر التي تنفذها الواجهة على حقل الإدخال */
interface KeyboardActions {
    fun commit(text: String)
    fun delete()
    fun enter()
    fun replaceLastWord(newText: String)   // للزخرفة الفورية
}

class AIKeyboardService :
    InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // ---- ربط دورة الحياة (مطلوب لـ Compose داخل الـ IME) ----
    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedState.savedStateRegistry

    // ---- إجراءات لوحة المفاتيح ----
    private val actions = object : KeyboardActions {
        override fun commit(text: String) {
            currentInputConnection?.commitText(text, 1)
        }

        override fun delete() {
            val ic = currentInputConnection ?: return
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingTextInCodePoints(1, 0) // يدعم الإيموجي والرموز المركبة
            }
        }

        override fun enter() {
            val ic = currentInputConnection ?: return
            val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        override fun replaceLastWord(newText: String) {
            val ic = currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
            val word = before.takeLastWhile { !it.isWhitespace() }
            ic.beginBatchEdit()
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(newText, 1)
            ic.endBatchEdit()
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        // ضروري حتى يعمل Compose داخل نافذة الـ IME
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@AIKeyboardService)
            setViewTreeViewModelStoreOwner(this@AIKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@AIKeyboardService)
            setContent { KeyboardRoot(actions) }   // سنبنيها في الخطوة التالية
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        registry.currentState = Lifecycle.State.STARTED
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }
}

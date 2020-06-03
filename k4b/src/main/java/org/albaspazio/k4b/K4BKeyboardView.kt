package org.albaspazio.k4b

import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.inputmethod.InputMethodSubtype


class K4BKeyboardView : KeyboardView {
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle)

    override fun onLongPress(key: Keyboard.Key): Boolean {

        return when(key.codes[0]){

            K4BKeyboard.KEYCODE_LEFT_SHIFT -> {
                onKeyboardActionListener.onKey(K4BKeyboard.KEYCODE_LONG_LEFT_SHIFT, IntArray(1){ K4BKeyboard.KEYCODE_LEFT_SHIFT })
                true
            }
            K4BKeyboard.KEYCODE_RIGHT_SHIFT -> {
                onKeyboardActionListener.onKey(K4BKeyboard.KEYCODE_LONG_RIGHT_SHIFT, IntArray(1){ K4BKeyboard.KEYCODE_LONG_RIGHT_SHIFT })
                true
            }
            Keyboard.KEYCODE_DELETE -> {
                onKeyboardActionListener.onKey(K4BKeyboard.KEYCODE_LONG_DELETE, IntArray(1){ K4BKeyboard.KEYCODE_LONG_DELETE })
                true
            }
            K4BKeyboard.KEYCODE_RECOGNIZE -> {
                onKeyboardActionListener.onKey(K4BKeyboard.KEYCODE_LONG_RECOGNIZE, IntArray(1){ K4BKeyboard.KEYCODE_LONG_RECOGNIZE })
                true
            }
            else -> super.onLongPress(key)
        }
    }


    fun setSubtypeOnSpaceKey(subtype: InputMethodSubtype?) {
//        val keyboard: K4BKeyboard = keyboard as K4BKeyboard
        //keyboard.setSpaceIcon(getResources().getDrawable(subtype.getIconResId()));
        invalidateAllKeys()
    }

//    override fun performClick(): Boolean {
//        return super.performClick()
//    }
}
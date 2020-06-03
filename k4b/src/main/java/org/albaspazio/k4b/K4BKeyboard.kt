package org.albaspazio.k4b

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.inputmethodservice.Keyboard
import android.view.inputmethod.EditorInfo
import java.text.DecimalFormatSymbols

class K4BKeyboard(context: Context?, xmlLayoutResId: Int, private var resources:Resources, private var isCharBased:Boolean=true) : Keyboard(context, xmlLayoutResId) {

//    constructor(context: Context?, layoutTemplateResId: Int, characters: CharSequence?, columns: Int, horizontalPadding: Int, resources:Resources) :
//            super(context, layoutTemplateResId, characters, columns, horizontalPadding) {
//        this.resources = resources
//    }

    companion object{
        const val KEYCODE_PLAYBACK_TEXT     = -50
        const val KEYCODE_EMPTY             = -60
        const val KEYCODE_RECOGNIZE         = -70
        const val KEYCODE_LONG_RECOGNIZE    = -71
        const val KEYCODE_DECIMAL_SEPARATOR = -80
        const val KEYCODE_LEFT_SHIFT        = -90
        const val KEYCODE_RIGHT_SHIFT       = -100
        const val KEYCODE_LONG_LEFT_SHIFT   = -91
        const val KEYCODE_LONG_RIGHT_SHIFT  = -101
        const val KEYCODE_LONG_DELETE       = -110
        const val EMPTY_CHAR                = ""
    }

    private var mEnterKey: Key? = null
    private var mBackKey: Key?  = null
    private var mModeChangeKey: Key? = null
    private var mSpaceKey: Key? = null
    private var mDeleteKey: Key? = null

    private var mDecSepKey: Key?  = null
    private var mPlaybackKey: Key?  = null
    private var mRecognizeKey: Key?  = null

    private var mLeftShiftKey: Key?  = null
    private var mRightShiftKey: Key?  = null

    override fun createKeyFromXml(res: Resources?, parent: Row?, x: Int, y: Int, parser: XmlResourceParser?): Key? {

        val key = Key(res, parent, x, y, parser)
        when(key.codes[0]){
            KEYCODE_DONE                ->  mEnterKey       = key
            KEYCODE_CANCEL              ->  mBackKey        = key
            KEYCODE_MODE_CHANGE         ->  mModeChangeKey  = key
            KEYCODE_DELETE              ->  mDeleteKey      = key
            32                          ->  mSpaceKey       = key

            KEYCODE_DECIMAL_SEPARATOR ->  mDecSepKey      = key
            KEYCODE_PLAYBACK_TEXT ->  mPlaybackKey    = key
            KEYCODE_RECOGNIZE ->  mRecognizeKey   = key

            KEYCODE_LEFT_SHIFT ->  mLeftShiftKey    = key
            KEYCODE_RIGHT_SHIFT ->  mRightShiftKey   = key
        }
        return key
    }

    fun getSelectKeyLabel(keycode: Int):Pair<String,String> {
        return when(keycode){
                                                // text to playback                                             text to append
            KEYCODE_DONE                        -> Pair(resources.getString(R.string.k4b_char_done),
                EMPTY_CHAR
            )
            KEYCODE_CANCEL                      -> Pair(resources.getString(R.string.k4b_char_cancel),
                EMPTY_CHAR
            )
            KEYCODE_MODE_CHANGE                 -> Pair(resources.getString(R.string.k4b_char_change_subtype),
                EMPTY_CHAR
            )
            KEYCODE_DELETE                      -> {
                                                    if(isCharBased)  Pair(resources.getString(
                                                        R.string.k4b_char_delete),
                                                        EMPTY_CHAR
                                                    )
                                                    else            Pair(resources.getString(
                                                        R.string.k4b_word_delete),
                                                        EMPTY_CHAR
                                                    )
                                                   }
            32                                  -> Pair(resources.getString(R.string.k4b_char_space),           " ")

            KEYCODE_DECIMAL_SEPARATOR -> resolveLocalDecimalPoint()
            KEYCODE_PLAYBACK_TEXT -> Pair(resources.getString(
                R.string.k4b_char_playback_text),
                EMPTY_CHAR
            )

            KEYCODE_RECOGNIZE -> Pair(resources.getString(
                R.string.k4b_word_recognize),
                EMPTY_CHAR
            )
            KEYCODE_LONG_RECOGNIZE -> Pair(resources.getString(
                R.string.k4b_word_long_recognize),
                EMPTY_CHAR
            )

            KEYCODE_LEFT_SHIFT -> {
                                                    if(isCharBased)  Pair(resources.getString(
                                                        R.string.k4b_char_left_shift),
                                                        EMPTY_CHAR
                                                    )
                                                    else            Pair(resources.getString(
                                                        R.string.k4b_word_left_shift),
                                                        EMPTY_CHAR
                                                    )
                                                   }

            KEYCODE_RIGHT_SHIFT -> {
                                                    if(isCharBased)  Pair(resources.getString(
                                                        R.string.k4b_char_right_shift),
                                                        EMPTY_CHAR
                                                    )
                                                    else            Pair(resources.getString(
                                                        R.string.k4b_word_right_shift),
                                                        EMPTY_CHAR
                                                    )
                                                   }

            KEYCODE_LONG_DELETE -> {    // doubt!!. to maximize uniformity between the two modalities
                                                        // also in the keyboard I delete all (instead of deleting last word)
                                                    if(isCharBased)  Pair(resources.getString(
                                                        R.string.k4b_all_delete),
                                                        EMPTY_CHAR
                                                    )
                                                    else            Pair(resources.getString(
                                                        R.string.k4b_all_delete),
                                                        EMPTY_CHAR
                                                    )
                                                   }

            KEYCODE_EMPTY -> Pair(resources.getString(
                R.string.k4b_char_empty),
                EMPTY_CHAR
            )
            44                                  -> Pair(resources.getString(R.string.k4b_char_comma),           ",")
            46                                  -> Pair(resources.getString(R.string.k4b_char_dot),             ".")
            else                                -> Pair(keycode.toChar().toString(),                            keycode.toChar().toString())
        }
    }

    fun resolveLocalDecimalPoint():Pair<String,String>{
        val separatorChar: Char = DecimalFormatSymbols.getInstance().decimalSeparator
        return if(separatorChar == '.') {
            mDecSepKey?.label = "."
            Pair(resources.getString(R.string.k4b_char_dot), ".")
        }
        else{
            mDecSepKey?.label = ","
            Pair(resources.getString(R.string.k4b_char_comma), ",")
        }
    }

    /**
     * This looks at the ime options given by the current editor, to set the
     * appropriate label on the keyboard's enter key (if it has one).
     */
    fun setImeOptions(res: Resources, options: Int) {
        if (mEnterKey == null) {
            return
        }
        when (options and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            EditorInfo.IME_ACTION_GO -> {
                mEnterKey!!.iconPreview = null
                mEnterKey!!.icon = null
                mEnterKey!!.label = res.getText(R.string.label_go_key)
            }
            EditorInfo.IME_ACTION_NEXT -> {
                mEnterKey!!.iconPreview = null
                mEnterKey!!.icon = null
                mEnterKey!!.label = res.getText(R.string.label_next_key)
            }
            EditorInfo.IME_ACTION_SEARCH -> {
                mEnterKey!!.icon = res.getDrawable(R.drawable.sym_keyboard_search)
                mEnterKey!!.label = null
            }
            EditorInfo.IME_ACTION_SEND -> {
                mEnterKey!!.iconPreview = null
                mEnterKey!!.icon = null
                mEnterKey!!.label = res.getText(R.string.label_send_key)
            }
            else -> {
                mEnterKey!!.icon = res.getDrawable(R.drawable.ic_send)
                mEnterKey!!.label = res.getText(R.string.label_send_key)
            }
        }
    }

//    internal class LatinKey(res: Resources?, parent: Row?, x: Int, y: Int, parser: XmlResourceParser?) :
//        Key(res, parent, x, y, parser) {
//        /**
//         * Overriding this method so that we can reduce the target area for the key that
//         * closes the keyboard.
//         */
//        override fun isInside(x: Int, y: Int): Boolean {
//            return super.isInside(x, if (codes[0] == KEYCODE_CANCEL) y - 10 else y)
//        }
//    }
}
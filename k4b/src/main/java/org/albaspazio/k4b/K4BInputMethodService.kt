package org.albaspazio.k4b

import android.Manifest
import android.content.Context
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import com.intentfilter.androidpermissions.PermissionManager
import com.intentfilter.androidpermissions.models.DeniedPermissions
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import org.albaspazio.core.accessory.Optional
import org.albaspazio.core.speech.SpeechManager
import org.albaspazio.core.speech.SpeechRecognitionManager
import java.util.*
import java.util.Collections.singleton

/*
IMPLEMENTS THE FOLLOWING IME:

KEYBOARD
    - TEXT:
        - free text
        - variable text * containing only chars+digits
                        * cannot start with number
                        * replace space with underscore
                        * no other symbols accepted)
                        * can be composed by different words, when inserted by speech. IME add an underscore among words
        - mathematical expressions :digits, -, + , :, x, (),[],{}
    - NUMERIC:
        - signed integer
        - signed decimal

SPEECH


CURSOR SHIFT:
- in keyboard mode:     char-by-char
- in speech rec mode:   word-by-word

Long press on cursor keys:
- right => send cursor to end
- left  => send cursor to home


the IME has a special feature. Should never use the back button. to modify the given text, user must confirm it with a specific key press.
otherwise keep the existing one.

TODO: check if connected to internet to allow speech recognition
 */

class K4BInputMethodService : InputMethodService(), OnKeyboardActionListener {

    private var mInputMethodManager:InputMethodManager? = null
    private lateinit var mCurrKeyboard:K4BKeyboard

    private var mInputType:Int = 0      // stores the attribute.inputType received in onStartInput
                                        // used when switching keyboard <=> speech

    private var mClassType:Int = 0      // stores the (attribute.inputType and InputType.TYPE_MASK_CLASS)

    private var isKeyboard:Boolean = true       // indicates whether is a keyboard or a speech recognizer

    private var isText:Boolean = true           // default is a text keyboard
    private var isDecimal:Boolean = false       // if false is an integer

    private lateinit var textK4B:K4BKeyboard
    private lateinit var integerK4B:K4BKeyboard
    private lateinit var decimalK4B:K4BKeyboard
    private lateinit var expressionsK4B:K4BKeyboard
    private lateinit var speechK4B:K4BKeyboard

    private lateinit var k4BView:K4BKeyboardView

    private var tempKeyCode:Int         = -1
    private var selectedKeyCode:Int     = -1
    private var selectedKeyLabel:Pair<String, String> = Pair("","")

    private val disposable              = CompositeDisposable()
    private val speechRelay             = PublishRelay.create<Optional<String>>()
    private lateinit var speechRecognizer: SpeechRecognitionManager
    private var haveAudioRecordPermission:Boolean = false

    private lateinit var speechManager:SpeechManager

    private var firstTapTime:Long       = 0
    private val DBLPRESS_THRESHOLD:Long = 500

    private var mHandler: Handler       = Handler()

    private var sOldText:String         = ""        // var holding text already present when the IME was opened
    private var sText:String            = ""        // var holding composed text
    private var firstEditedPosition:Int = 0


    companion object {
        const val TYPE_TEXT_VARIATION_MATH_EXPRESSION:Int = 1026
        const val TYPE_TEXT_VARIATION_SINGLE_WORD:Int = 1025
    }

    //========================================================================================================================================
    // LIFECYCLE OVERRIDE
    //========================================================================================================================================
    // #1
    override fun onCreate() {
        super.onCreate()
        Log.i("K4BInputMethodService", "onCreate")
        mInputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        speechManager       = SpeechManager(resources, applicationContext)
        speechRecognizer    = SpeechRecognitionManager(this)

        checkPermissions()      // just set haveAudioRecordPermission
    }

    /** #2
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change. */
    override fun onInitializeInterface(){
        textK4B                 = K4BKeyboard(this, R.xml.qwerty_keys, resources, true)
        integerK4B              = K4BKeyboard(this, R.xml.numeric_integer_keys, resources, true)
        decimalK4B              = K4BKeyboard(this, R.xml.numeric_keys, resources, true)
        expressionsK4B          = K4BKeyboard(this, R.xml.math_expressions_keys, resources, true)

        speechK4B               = K4BKeyboard(this, R.xml.speech_keys, resources, false)
        isKeyboard              = true
    }

    /** #3 and after back press #1
     * This is the main point where we do our initialization of the input method to begin operating on an application.
     * At this point we have been bound to the client, and are now receiving all of the detailed information
     * about the target of our edits. */
    // here comes when i reopen the ime after having closed with backbutton
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mInputType      = attribute.inputType
        mClassType      = mInputType and InputType.TYPE_MASK_CLASS

        mCurrKeyboard   = getKeyboardByAttribute(mInputType, mClassType)    // set isDecimal, isText
        mCurrKeyboard.setImeOptions(resources, attribute.imeOptions)        // Update the label on the enter key, depending on what the application says it will do.
        mCurrKeyboard.resolveLocalDecimalPoint()                            // set label of the decimal separator key according to locale settings
    }

    /** #4
     This will be called the first time your input method is displayed
     and every time it needs to be re-created such as due to a configuration change.*/
    override fun onCreateInputView(): View {
//        val list:List<InputMethodInfo>  = mInputMethodManager!!.inputMethodList
//        mInputMethodManager!!.showInputMethodAndSubtypeEnabler("org.albaspazio.k4b/.K4BInputMethodService")

        k4BView = layoutInflater.inflate(R.layout.ime_view, null) as K4BKeyboardView
        setKeyboard(mCurrKeyboard)
        return k4BView
    }
    // #5
//    override fun setInputView(view: View) {
//        super.setInputView(view)
//    }

    // #6 and after back press #2
    // here comes when i reopen the ime after having closed with handleClose(). so I put here current string detection
    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)

        sOldText            = currentInputConnection.getTextBeforeCursor(9999, 0)?.toString() ?: ""
        sText               = sOldText
        firstEditedPosition = sOldText.length

        val msg =   if(sText.isNotEmpty())  resources.getString(R.string.k4b_start_editing_notempty_text, sText)
                    else                    resources.getString(R.string.k4b_start_editing_empty_text)

        speechManager.speak(msg, TextToSpeech.QUEUE_FLUSH)

        // Apply the selected keyboard to the input view.
        setKeyboard(mCurrKeyboard)
        k4BView.closing()       // ?????
        val subtype = mInputMethodManager!!.currentInputMethodSubtype
        k4BView.setSubtypeOnSpaceKey(subtype)
    }

    override fun onFinishInput() {
        sText = ""
        mCurrKeyboard = textK4B
        k4BView.closing()
        super.onFinishInput()
    }

    override fun onDestroy() {
        speechManager.shutdown()    // release TTS
        disposable.clear()
        super.onDestroy()
    }

    // END LIFECYCLE OVERRIDE
    //========================================================================================================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        when (keyCode) {
            KeyEvent.KEYCODE_BACK ->         // skip back
                return true
        }
        return super.onKeyDown(keyCode, event)
    }

    //========================================================================================================================================
    //  END OVERRIDE
    //========================================================================================================================================


    // set given K4Keyboard or switch keyboard
    private fun setKeyboard(kb:K4BKeyboard?, playback:Boolean=false): K4BKeyboard {

        mCurrKeyboard = kb ?:   if(isKeyboard){
                                    isKeyboard = !isKeyboard
                                    if(playback)
                                        speechManager.speak(resources.getString(
                                            R.string.k4b_modality_changed, resources.getString(
                                                R.string.k4b_modality_speech)),
                                                            TextToSpeech.QUEUE_FLUSH)
                                    speechK4B
                                }
                                else {
                                    isKeyboard = !isKeyboard
                                    if(playback)
                                        speechManager.speak(resources.getString(
                                            R.string.k4b_modality_changed, resources.getString(
                                                R.string.k4b_modality_keyboard)),
                                                            TextToSpeech.QUEUE_FLUSH)
                                    getKeyboardByAttribute(mInputType, mClassType)
                                }

        k4BView.keyboard = mCurrKeyboard
        k4BView.setOnKeyboardActionListener(this)
        return mCurrKeyboard
    }

    // if same key and time distance<threshold => is a dblpress
    private fun checkDblPress(keycode:Int):Boolean{
        val elapsed = Date().time - firstTapTime
        return (keycode == tempKeyCode && elapsed < DBLPRESS_THRESHOLD)
    }

    // first detect long press key (detected in K4BKeyboardView).
    // KEYCODE_LONG_LEFT_SHIFT/KEYCODE_LONG_RIGHT_SHIFT are executed immediately
    // KEYCODE_LONG_DELETE & KEYCODE_LONG_RECOGNIZE acts as normal selections that needs a double tap
    override fun onKey(primaryCode: Int, keyCodes: IntArray) {
        if(currentInputConnection != null) {

            // check longtap special key
            when(primaryCode){
                K4BKeyboard.KEYCODE_LONG_LEFT_SHIFT -> {
                    currentInputConnection.setSelection(0, 0)
                    speechManager.speak(resources.getString(R.string.k4b_cursor_shifted_home), TextToSpeech.QUEUE_FLUSH)
                }

                K4BKeyboard.KEYCODE_LONG_RIGHT_SHIFT -> {
                    val extractedText: ExtractedText? = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
                    if(extractedText?.text == null) return
                    val index: Int = extractedText.text.length
                    currentInputConnection.setSelection(index, index)
                    speechManager.speak(resources.getString(R.string.k4b_cursor_shifted_end), TextToSpeech.QUEUE_FLUSH)
                }

                // otherwise proceed normally
                else -> {
                    when(checkDblPress(primaryCode)){
                        true -> applyKey(primaryCode)
                        false -> {
                            tempKeyCode     = primaryCode
                            firstTapTime    = Date().time
                            mHandler.postDelayed({setKey(primaryCode)}, DBLPRESS_THRESHOLD + 1)
                        }
                    }
                }
            }
        }
    }

    // called delayed after single onKey.
    // if is trying to change modality and cannot record audio. say it and abort change
    private fun setKey(keycode:Int){
        selectedKeyCode  = keycode
        selectedKeyLabel =  if(keycode == Keyboard.KEYCODE_MODE_CHANGE && !haveAudioRecordPermission)   Pair(resources.getString(
            R.string.k4b_audiorecord_permission_denied),"")
                            else                                                                        mCurrKeyboard.getSelectKeyLabel(keycode)

        speechManager.stopAndSpeak(selectedKeyLabel.first, TextToSpeech.QUEUE_FLUSH)
    }

    // called when a dblkey has been detected
    // if is trying to change modality and cannot record audio. say it and abort change
    private fun applyKey(lastkey:Int){

        mHandler.removeCallbacksAndMessages(null)

        if(selectedKeyCode == -1)   selectedKeyCode = lastkey
        when (selectedKeyCode)
        {
            Keyboard.KEYCODE_DELETE -> {
                if(isKeyboard)  deleteLastChars(1)              // delete previous char
                else            deleteLastWord()                    // delete previous word
            }

            K4BKeyboard.KEYCODE_LONG_DELETE -> clear(true)  // delete whole text

            Keyboard.KEYCODE_DONE -> {                              // confirm text and close IME
                speechManager.speak(resources.getString(R.string.k4b_text_confirmed, sText), TextToSpeech.QUEUE_FLUSH)
                handleClose()
            }

            Keyboard.KEYCODE_CANCEL -> {                            // close IME, restoring previous text
                revertAndClose()
            }

            Keyboard.KEYCODE_MODE_CHANGE -> {                       // switch between keyboard <-> speechrecognition
                if(!haveAudioRecordPermission)
                    speechManager.stopAndSpeak(resources.getString(R.string.k4b_audiorecord_permission_denied), TextToSpeech.QUEUE_FLUSH)
                else
                    setKeyboard(null, true)
            }

            K4BKeyboard.KEYCODE_LEFT_SHIFT -> {
                if(isKeyboard)  shiftCursorByChar(false)    // shift cursor to previous char
                else            shiftCursorByWord(false)    // shift cursor to previous word
            }

            K4BKeyboard.KEYCODE_RIGHT_SHIFT -> {
                if(isKeyboard)  shiftCursorByChar(true)     // shift cursor to next char
                else            shiftCursorByWord(true)     // shift cursor to next word
            }

            K4BKeyboard.KEYCODE_PLAYBACK_TEXT -> {                  // playback text up to cursor
                if(sText.isNotEmpty()) {
                    val cursor:Int = getCursorPosition()
                    var text = sText.substring(0, cursor)

                    // if text ends with a space, warn user
                    if(text[text.length-1].toString() == " ")
                       text = "$text ${resources.getString(R.string.k4b_char_final_space_char)}"

                    speechManager.speak(text, TextToSpeech.QUEUE_FLUSH)
                }
                else
                    speechManager.speak(resources.getString(R.string.k4b_char_playback_text_empty), TextToSpeech.QUEUE_FLUSH)
            }

            K4BKeyboard.KEYCODE_RECOGNIZE       -> recognizeSpeech(false)      // append new text to the current one
            K4BKeyboard.KEYCODE_LONG_RECOGNIZE  -> recognizeSpeech(true)       // replace current text with the new one

            // INSERTING A CHARACTER
            else -> {
                speechManager.speak(resources.getString(R.string.k4b_char_inserted, selectedKeyLabel.first), TextToSpeech.QUEUE_FLUSH)
                currentInputConnection.commitText(selectedKeyLabel.second, 1)
                sText += selectedKeyLabel.second
            }
        }
    }

    //============================================================================================================
    // FINISHING
    // delete present text and restore the one already present when the IME was opended
    private fun revertAndClose() {
        clear()
        currentInputConnection.commitText(sOldText,0)
        speechManager.speak(resources.getString(R.string.k4b_text_aborted), TextToSpeech.QUEUE_FLUSH)
        handleClose()
    }

    private fun handleClose() {
        requestHideSelf(0)
        disposable.clear()
        k4BView.closing()
    }

    //============================================================================================================
    // DELETE TEXT
    // delete all text
    private fun clear(playback:Boolean=false){
        currentInputConnection.setSelection(0, 0)
        currentInputConnection.deleteSurroundingText(0, sText.length)
        sText = ""

        if(playback) speechManager.speak(resources.getString(R.string.k4b_all_deleted), TextToSpeech.QUEUE_FLUSH)
    }

    // delete nchars before cursor
    private fun deleteLastChars(nchars:Int){
        currentInputConnection.deleteSurroundingText(nchars, 0)
        val removed = sText.substring((sText.length - nchars - 1).coerceAtLeast(0), sText.length)
        speechManager.speak(resources.getString(R.string.k4b_char_deleted, removed), TextToSpeech.QUEUE_FLUSH)
        sText = sText.dropLast(nchars)
    }

    // delete previous word before cursor
    private fun deleteLastWord(){
        val lastword = getWordIndices(sText)    // get indices of previous word, preserve the last space char
        if(lastword.first == BreakIterator.DONE)
            speechManager.speak(lastword.third, TextToSpeech.QUEUE_FLUSH)
        else
            deleteLastChars(lastword.third.length)
    }

    //============================================================================================================
    // CURSOR MANAGEMENT
    private fun shiftCursorByChar(rightward:Boolean){

        val cursor = getCursorPosition()
        if(rightward)
        {
            if(cursor == sText.length)
                speechManager.speak(resources.getString(R.string.k4b_cursor_already_end),TextToSpeech.QUEUE_FLUSH)
            else {
                currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                speechManager.speak(resources.getString(R.string.k4b_char_cursor_right_shifted),TextToSpeech.QUEUE_FLUSH)
            }
        }
        else
        {
            if(cursor == 0)
                speechManager.speak(resources.getString(R.string.k4b_cursor_already_home),TextToSpeech.QUEUE_FLUSH)
            else {
                currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                currentInputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT))
                speechManager.speak(resources.getString(R.string.k4b_char_cursor_left_shifted), TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun shiftCursorByWord(rightward:Boolean){
        if(rightward){
            val indices = getWordIndices(sText, false)
            currentInputConnection.setSelection(indices.second, indices.second)
            speechManager.speak(resources.getString(R.string.k4b_word_cursor_right_shifted), TextToSpeech.QUEUE_FLUSH)
        }
        else
        {
            val indices = getWordIndices(sText, true)
            currentInputConnection.setSelection(indices.first, indices.first)
            speechManager.speak(resources.getString(R.string.k4b_word_cursor_left_shifted), TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun getCursorPosition():Int{
        val extractedText: ExtractedText = currentInputConnection.getExtractedText(ExtractedTextRequest(), 0)
        return (extractedText.startOffset + extractedText.selectionEnd)   // val startIndex = extractedText.startOffset + extractedText.selectionStart
    }

    // word-wise mode: assumes that cursor can be only at the end of a word
    private fun getWordIndices(text:String, isprevious:Boolean=true):Triple<Int, Int, String>{
        val boundary: BreakIterator = BreakIterator.getLineInstance()
        boundary.setText(text)
        val cursor = getCursorPosition()

        return when(isprevious){
            true -> {
                val st = boundary.preceding(cursor)
                if(st != BreakIterator.DONE)    Triple(st, cursor, text.substring(st, cursor))
                else                            Triple(BreakIterator.DONE, BreakIterator.DONE, resources.getString(
                    R.string.k4b_cursor_already_home))
            }
            false -> {
                val end = boundary.following(cursor)
                if(end != BreakIterator.DONE)   Triple(cursor, end, text.substring(cursor, end))
                else                            Triple(BreakIterator.DONE, BreakIterator.DONE, resources.getString(
                    R.string.k4b_cursor_already_end))
            }
        }
    }

    //============================================================================================================
    // SPEECH REC
    private fun recognizeSpeech(substitute:Boolean, valid_results:List<String> = listOf()){
        speechRecognizer.getSpeechInput()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = {

                    when (it.first) {
                        SpeechRecognitionManager.REC_SUCCESS -> {
                            Log.d("", "recognized word $it")

                            // check whether given response is allowed
                            val res:Boolean =   if(valid_results.isEmpty())     true
                                                else                            valid_results.contains(it.second)

                            if (res) {
                                val newword =   if (substitute) {
                                                    clear()
                                                    it
                                                } else {
                                                    if (sText.isNotEmpty()) " $it"
                                                    else it
                                                }
                                sText += newword
                                currentInputConnection.commitText(newword.toString(), newword.toString().length)
                                speechManager.speak(resources.getString(R.string.k4b_char_inserted, it), TextToSpeech.QUEUE_FLUSH)

                            } else
                            // text recognized but not allowed
                                speechManager.speak(resources.getString(org.albaspazio.core.R.string.char_recognition_wrong), TextToSpeech.QUEUE_FLUSH, clb={ recognizeSpeech(substitute, valid_results)})
                        }
                        else ->
                            // RECOGNIZER ERROR
                            speechManager.speak(it.second!!, TextToSpeech.QUEUE_FLUSH, clb={ recognizeSpeech(substitute, valid_results) })
                    }
                },
                onError = {
                    speechManager.speak(resources.getString(R.string.k4b_char_recognition_error, it.toString()),TextToSpeech.QUEUE_FLUSH)
                    Log.e("", it.toString())
                }
            )
            .addTo(disposable)
    }

    //============================================================================================================
    // ACCESSORY
    //============================================================================================================
    private fun getKeyboardByAttribute(inputtype:Int, classtype:Int):K4BKeyboard{
        return when (classtype) {
            InputType.TYPE_CLASS_NUMBER /*, InputType.TYPE_CLASS_DATETIME*/ -> {

                isText = false
                val flag = inputtype and InputType.TYPE_MASK_FLAGS
                if(InputType.TYPE_NUMBER_FLAG_DECIMAL and flag > 0){
                    isDecimal = true
                    decimalK4B
                }
                else {
                    isDecimal = false
                    integerK4B   // Numbers and dates default to the symbols keyboard, with no extra features.
                }
            }

            InputType.TYPE_CLASS_TEXT -> {
                // This is general text editing.  We will default to the normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the user types).
                isText      = true
                isDecimal   = false
                textK4B
            }
            else ->     textK4B // For all unknown input types, default to the alphabetic keyboard with no special features.
        }
    }

    // just set haveAudioRecordPermission
    private fun checkPermissions(){
        val permissionManager: PermissionManager = PermissionManager.getInstance(applicationContext)
        permissionManager.checkPermissions(singleton(Manifest.permission.RECORD_AUDIO),object : PermissionManager.PermissionRequestListener {
            override fun onPermissionGranted()                                      {haveAudioRecordPermission = true}
            override fun onPermissionDenied(deniedPermissions: DeniedPermissions)   {haveAudioRecordPermission = false}
        })
    }

    //============================================================================================================
    override fun onText(charSequence: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    //============================================================================================================
    // ISSUES
    // TODO: understand its usage
//    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
//        super.onCurrentInputMethodSubtypeChanged(subtype)
//        k4BView.setSubtypeOnSpaceKey(subtype)
//    }
//
//    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
//        var o = oldSelEnd + 1
//    }
    // TODO: never called
//    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo:CursorAnchorInfo){
//        val desc = cursorAnchorInfo.toString()
//    }
    //============================================================================================================
}
















/*
    //============================================================================================================
    // TRASH
    //============================================================================================================
    private fun applyKey(lastkey:Int):Boolean{

        when(lastkey){
            K4BKeyboard.KEYCODE_EMPTY -> {true}
            K4BKeyboard.KEYCODE_CHANGE_SUBIME -> {
                val res:Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)   switchToNextInputMethod(false /* onlyCurrentIme */)
                else                                                                    mInputMethodManager!!.switchToNextInputMethod(getToken(),false) // onlyCurrentIme
                res
            }
        }
     }
    private fun getToken(): IBinder? {
        val dialog = window ?: return null
        val window = dialog.window ?: return null
        return window.attributes.token
    }
 */

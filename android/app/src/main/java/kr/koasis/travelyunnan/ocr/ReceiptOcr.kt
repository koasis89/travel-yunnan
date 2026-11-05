package kr.koasis.travelyunnan.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** 영수증 사진 → 글자. ML Kit 중국어 인식(기기 내장 모델, 오프라인·무료). 사진은 밖으로 보내지 않는다 */
object ReceiptOcr {
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }

    suspend fun read(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return recognizer.process(image).await().text
    }
}

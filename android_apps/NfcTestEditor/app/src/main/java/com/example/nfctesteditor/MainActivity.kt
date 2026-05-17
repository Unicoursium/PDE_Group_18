package com.example.nfctesteditor

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset
import java.util.Locale

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var inputText: EditText
    private lateinit var writeButton: Button
    private lateinit var readButton: Button

    private var pendingWriteText: String? = null
    private var isWriteMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        inputText = findViewById(R.id.inputText)
        writeButton = findViewById(R.id.writeButton)
        readButton = findViewById(R.id.readButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        when {
            nfcAdapter == null -> {
                statusText.text = "This device does not support NFC."
            }

            nfcAdapter?.isEnabled == false -> {
                statusText.text = "NFC is supported but disabled. Please enable NFC in settings."
            }

            else -> {
                statusText.text = "Read Mode: tap an NFC tag to read it."
            }
        }

        writeButton.setOnClickListener {
            val text = inputText.text.toString()

            if (text.isBlank()) {
                statusText.text = "Please enter some text before writing."
                return@setOnClickListener
            }

            pendingWriteText = text
            isWriteMode = true
            statusText.text = "Write Mode: now tap an NFC tag to write this text."
            resultText.text = "Pending write value:\n$text"
        }

        readButton.setOnClickListener {
            pendingWriteText = null
            isWriteMode = false
            statusText.text = "Read Mode: tap an NFC tag to read it."
            resultText.text = "Waiting for NFC tag..."
        }
    }

    override fun onResume() {
        super.onResume()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: return

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V

        adapter.enableReaderMode(
            this,
            this,
            flags,
            null
        )
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        if (isWriteMode && pendingWriteText != null) {
            val textToWrite = pendingWriteText ?: return
            val writeResult = writeTextToTag(tag, textToWrite)

            runOnUiThread {
                if (writeResult.startsWith("Success")) {
                    statusText.text = "Write successful. Switched back to Read Mode."
                    pendingWriteText = null
                    isWriteMode = false
                } else {
                    statusText.text = "Write failed."
                }

                resultText.text = writeResult
            }
        } else {
            val readResult = readTag(tag)

            runOnUiThread {
                statusText.text = "Tag read successfully."
                resultText.text = readResult
            }
        }
    }

    private fun readTag(tag: Tag): String {
        val tagId = tag.id.toHexString()
        val technologies = tag.techList.joinToString(separator = "\n")

        val ndefInfo = readNdefRecords(tag)

        return """
            NFC Tag Detected
            
            Tag ID / UID:
            $tagId
            
            Technologies:
            $technologies
            
            NDEF Content:
            $ndefInfo
        """.trimIndent()
    }

    private fun readNdefRecords(tag: Tag): String {
        val ndef = Ndef.get(tag) ?: return "This tag does not contain readable NDEF data."

        return try {
            ndef.connect()

            val message = ndef.ndefMessage

            if (message == null || message.records.isEmpty()) {
                "No NDEF records found."
            } else {
                message.records.mapIndexed { index, record ->
                    """
                    Record ${index + 1}:
                    TNF: ${record.tnf}
                    Type HEX: ${record.type.toHexString()}
                    Type Text: ${record.type.decodeToStringSafe()}
                    Payload HEX: ${record.payload.toHexString()}
                    Parsed Value: ${parseNdefRecord(record)}
                    """.trimIndent()
                }.joinToString(separator = "\n\n")
            }
        } catch (e: Exception) {
            "Error reading NDEF: ${e.message}"
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeTextToTag(tag: Tag, text: String): String {
        val message = createTextNdefMessage(text)
        val size = message.toByteArray().size

        val ndef = Ndef.get(tag)

        if (ndef != null) {
            return try {
                ndef.connect()

                if (!ndef.isWritable) {
                    return "Failed: this NFC tag is not writable."
                }

                if (ndef.maxSize < size) {
                    return "Failed: message is too large.\nRequired: $size bytes\nTag capacity: ${ndef.maxSize} bytes"
                }

                ndef.writeNdefMessage(message)

                """
                Success: text written to NFC tag.
                
                Written Value:
                $text
                
                Message Size:
                $size bytes
                """.trimIndent()
            } catch (e: Exception) {
                "Failed to write NDEF: ${e.message}"
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        val formatable = NdefFormatable.get(tag)

        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)

                """
                Success: tag formatted and text written.
                
                Written Value:
                $text
                
                Message Size:
                $size bytes
                """.trimIndent()
            } catch (e: Exception) {
                "Failed to format/write tag: ${e.message}"
            } finally {
                try {
                    formatable.close()
                } catch (_: Exception) {
                }
            }
        }

        return "Failed: this tag does not support NDEF writing."
    }

    private fun createTextNdefMessage(text: String): NdefMessage {
        val record = NdefRecord.createTextRecord("en", text)
        return NdefMessage(arrayOf(record))
    }

    private fun parseNdefRecord(record: NdefRecord): String {
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                parseTextRecord(record)
            }

            record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_URI) -> {
                parseUriRecord(record)
            }

            record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                record.payload.decodeToStringSafe()
            }

            else -> {
                record.payload.decodeToStringSafe()
            }
        }
    }

    private fun parseTextRecord(record: NdefRecord): String {
        val payload = record.payload

        if (payload.isEmpty()) return ""

        val languageCodeLength = payload[0].toInt() and 0x3F
        val textStart = 1 + languageCodeLength

        if (textStart >= payload.size) return ""

        return String(
            payload,
            textStart,
            payload.size - textStart,
            Charset.forName("UTF-8")
        )
    }

    private fun parseUriRecord(record: NdefRecord): String {
        val payload = record.payload

        if (payload.isEmpty()) return ""

        val uriPrefixes = arrayOf(
            "",
            "http://www.",
            "https://www.",
            "http://",
            "https://",
            "tel:",
            "mailto:",
            "ftp://anonymous:anonymous@",
            "ftp://ftp.",
            "ftps://",
            "sftp://",
            "smb://",
            "nfs://",
            "ftp://",
            "dav://",
            "news:",
            "telnet://",
            "imap:",
            "rtsp://",
            "urn:",
            "pop:",
            "sip:",
            "sips:",
            "tftp:",
            "btspp://",
            "btl2cap://",
            "btgoep://",
            "tcpobex://",
            "irdaobex://",
            "file://",
            "urn:epc:id:",
            "urn:epc:tag:",
            "urn:epc:pat:",
            "urn:epc:raw:",
            "urn:epc:",
            "urn:nfc:"
        )

        val prefixIndex = payload[0].toInt() and 0xFF
        val prefix = uriPrefixes.getOrElse(prefixIndex) { "" }

        val uriBody = String(
            payload,
            1,
            payload.size - 1,
            Charset.forName("UTF-8")
        )

        return prefix + uriBody
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") {
            String.format(Locale.US, "%02X", it)
        }
    }

    private fun ByteArray.decodeToStringSafe(): String {
        return try {
            String(this, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            "[Not valid UTF-8 text]"
        }
    }
}
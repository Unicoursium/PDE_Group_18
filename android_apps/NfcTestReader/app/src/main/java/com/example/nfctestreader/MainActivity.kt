package com.example.nfctestreader

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset
import java.util.Locale

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        when {
            nfcAdapter == null -> {
                statusText.text = "This device does not support NFC."
            }

            nfcAdapter?.isEnabled == false -> {
                statusText.text = "NFC is supported but currently disabled. Please enable NFC in Android settings."
            }

            else -> {
                statusText.text = "NFC is ready. Tap a tag to the back of your phone."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        adapter.enableReaderMode(
            this,
            this,
            flags,
            null
        )
    }

    private fun disableNfcReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val tagId = tag.id.toHexString()
        val technologies = tag.techList.joinToString(separator = "\n")

        val ndefInfo = readNdefValue(tag)

        val result = """
            NFC Tag Detected
            
            Tag ID / UID:
            $tagId
            
            Technologies:
            $technologies
            
            NDEF Value:
            $ndefInfo
        """.trimIndent()

        runOnUiThread {
            statusText.text = "Tag read successfully."
            resultText.text = result
        }
    }

    private fun readNdefValue(tag: Tag): String {
        val ndef = Ndef.get(tag) ?: return "This tag does not contain readable NDEF data."

        return try {
            ndef.connect()

            val message: NdefMessage? = ndef.ndefMessage

            if (message == null || message.records.isEmpty()) {
                "No NDEF records found."
            } else {
                message.records.mapIndexed { index, record ->
                    """
                    Record ${index + 1}:
                    TNF: ${record.tnf}
                    Type: ${record.type.toHexString()} (${record.type.decodeToStringSafe()})
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
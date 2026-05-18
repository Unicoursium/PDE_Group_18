package com.example.ulink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.charset.Charset
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_bold, FontWeight.ExtraBold)
)

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var showHintBar by mutableStateOf(true)
    private var isLinked by mutableStateOf(false)
    private var nfcId by mutableStateOf("--")
    private var profileValue by mutableStateOf("--")

    private var hintLeft by mutableStateOf("Let’s Start With")
    private var hintRight by mutableStateOf("A Single Tap!")

    private var isScanning by mutableStateOf(false)
    private fun unlinkWristband() {
        isLinked = false
        nfcId = "--"
        profileValue = "--"

        hintLeft = "Let’s Start With"
        hintRight = "A Single Tap!"
        showHintBar = true

        isScanning = false
        nfcAdapter?.disableReaderMode(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            MainApp(
                isLinked = isLinked,
                nfcId = nfcId,
                profileValue = profileValue,
                hintLeft = hintLeft,
                hintRight = hintRight,
                showHintBar = showHintBar,
                onAddClick = {
                    startNfcScan()
                },
                onUnlinkClick = {
                    unlinkWristband()
                }
            )
        }

    }




    override fun onResume() {
        super.onResume()
        if (isScanning) {
            enableNfcReaderMode()
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun startNfcScan() {
        val adapter = nfcAdapter

        if (adapter == null) {
            hintLeft = "NFC Not"
            hintRight = "Supported"
            return
        }

        if (!adapter.isEnabled) {
            hintLeft = "Please Enable"
            hintRight = "NFC"
            return
        }

        isScanning = true
        showHintBar = true
        hintLeft = "Now Tap the"
        hintRight = "Wristband"

        enableNfcReaderMode()
    }

    private fun enableNfcReaderMode() {
        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V

        nfcAdapter?.enableReaderMode(
            this,
            this,
            flags,
            null
        )
    }

    override fun onTagDiscovered(tag: Tag) {
        val tagId = tag.id.toHexString()
        val tagValue = readNdefValue(tag).ifBlank { "--" }

        runOnUiThread {
            nfcId = tagId
            profileValue = tagValue
            isLinked = true

            hintLeft = "Let’s Start With"
            hintRight = "A Single Tap!"
            showHintBar = false

            isScanning = false
            nfcAdapter?.disableReaderMode(this)
        }
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        "%02X".format(byte)
    }
}

private fun readNdefValue(tag: Tag): String {
    val ndef = Ndef.get(tag) ?: return ""

    return try {
        ndef.connect()

        val message = ndef.ndefMessage ?: return ""
        val record = message.records.firstOrNull() ?: return ""

        decodeNdefRecord(record)
    } catch (e: Exception) {
        ""
    } finally {
        try {
            if (ndef.isConnected) {
                ndef.close()
            }
        } catch (_: Exception) {
        }
    }
}

private fun decodeNdefRecord(record: NdefRecord): String {
    return when {
        record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
            decodeTextRecord(record)
        }

        record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_URI) -> {
            decodeUriRecord(record)
        }

        record.payload.isNotEmpty() -> {
            record.payload.toString(Charsets.UTF_8).trim()
        }

        else -> ""
    }
}

private fun decodeTextRecord(record: NdefRecord): String {
    val payload = record.payload
    if (payload.isEmpty()) return ""

    val statusByte = payload[0].toInt()
    val languageCodeLength = statusByte and 0x3F
    val isUtf16 = statusByte and 0x80 != 0

    val charset = if (isUtf16) {
        Charset.forName("UTF-16")
    } else {
        Charsets.UTF_8
    }

    val textStart = 1 + languageCodeLength
    if (textStart >= payload.size) return ""

    return payload
        .copyOfRange(textStart, payload.size)
        .toString(charset)
        .trim()
}

private fun decodeUriRecord(record: NdefRecord): String {
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

    val prefixIndex = payload[0].toInt()
    val prefix = uriPrefixes.getOrElse(prefixIndex) { "" }

    val uriBody = payload
        .copyOfRange(1, payload.size)
        .toString(Charsets.UTF_8)

    return "$prefix$uriBody".trim()
}


@Composable
fun MainApp(
    isLinked: Boolean,
    nfcId: String,
    profileValue: String,
    hintLeft: String,
    hintRight: String,
    showHintBar: Boolean,
    onAddClick: () -> Unit,
    onUnlinkClick: () -> Unit
) {
    MaterialTheme {
        HomeScreen(
            isLinked = isLinked,
            nfcId = nfcId,
            profileValue = profileValue,
            hintLeft = hintLeft,
            hintRight = hintRight,
            showHintBar = showHintBar,
            onAddClick = onAddClick,
            onUnlinkClick = onUnlinkClick
        )
    }
}

@Composable
fun AppText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    textDecoration: TextDecoration? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val maxAllowedScale = 1.10f

    val adjustedFontSizeValue: Float = fontSize.value * maxAllowedScale / fontScale
    val adjustedFontSize = adjustedFontSizeValue.sp

    val adjustedLineHeight = if (lineHeight == TextUnit.Unspecified) {
        TextUnit.Unspecified
    } else {
        val adjustedLineHeightValue: Float = lineHeight.value * maxAllowedScale / fontScale
        adjustedLineHeightValue.sp
    }

    Text(
        text = text,
        fontSize = adjustedFontSize,
        fontFamily = GoogleSans,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textDecoration = textDecoration,
        lineHeight = adjustedLineHeight,
        textAlign = textAlign
    )
}

@Composable
fun HomeScreen(
    isLinked: Boolean,
    nfcId: String,
    profileValue: String,
    hintLeft: String,
    hintRight: String,
    showHintBar: Boolean,
    onAddClick: () -> Unit,
    onUnlinkClick: () -> Unit
) {
    val purple = Color(0xFF6A35E8)
    val textDark = Color(0xFF262633)
    val greyText = Color(0xFFA8A8A8)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF8FFFC),
                        Color(0xFFFFFFFF),
                        Color(0xFFF5F0FF)
                    )
                )
            )
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth

        val horizontalPadding = if (screenWidth < 370.dp) 16.dp else 22.dp
        val bottomAreaHeight = if (screenHeight < 760.dp) 160.dp else 175.dp
        val topPadding = if (screenHeight < 760.dp) 28.dp else screenHeight * 0.045f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .padding(
                    top = topPadding,
                    bottom = bottomAreaHeight + 20.dp
                )
        ) {
            TopWelcomeCard(
                purple = purple,
                textDark = textDark
            )

            Spacer(modifier = Modifier.height(if (screenHeight < 760.dp) 20.dp else 28.dp))

            AppText(
                text = "Your Wristband",
                fontSize = if (screenWidth < 370.dp) 27.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textDark,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            WristbandCard(
                isLinked = isLinked,
                nfcId = nfcId,
                profileValue = profileValue,
                purple = purple,
                greyText = greyText,
                textDark = textDark,
                onUnlinkClick = onUnlinkClick
            )
        }

        BottomArea(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(bottomAreaHeight),
            purple = purple,
            hintLeft = hintLeft,
            hintRight = hintRight,
            showHintBar = showHintBar,
            onAddClick = onAddClick
        )
    }
}

data class DateCardData(
    val month: String,
    val day: String,
    val weekday: String
)

@Composable
fun rememberDateCardData(): DateCardData {
    var currentDate by remember {
        mutableStateOf(LocalDate.now())
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentDate = LocalDate.now()
            delay(60_000L)
        }
    }

    return DateCardData(
        month = currentDate.format(
            DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        ),
        day = currentDate.dayOfMonth.toString(),
        weekday = currentDate.format(
            DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        )
    )
}

@Composable
fun TopWelcomeCard(
    purple: Color,
    textDark: Color
) {
    val dateCard = rememberDateCardData()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFC5A7FF),
                        Color(0xFFE6DFFF)
                    )
                )
            )
            .padding(22.dp)
    ) {
        // Top row: avatar + text + bell
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.avatar),
                contentDescription = "User avatar",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                AppText(
                    text = "Hello!",
                    fontSize = 21.sp,
                    color = textDark,
                    maxLines = 1
                )

                AppText(
                    text = "Please Login",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textDark,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notification",
                tint = textDark,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 6.dp)
            )
        }

        // Bottom left text, with right space reserved for date card
        AppText(
            text = "How Are You Today?",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7C4DFF),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    end = 96.dp,
                    bottom = 34.dp
                )
        )

        // Date card
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 74.dp, height = 92.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(purple),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText(
                    text = dateCard.month,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    lineHeight = 21.sp
                )

                AppText(
                    text = dateCard.day,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    lineHeight = 38.sp
                )


                AppText(
                    text = dateCard.weekday,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
fun WristbandCard(
    isLinked: Boolean,
    nfcId: String,
    profileValue: String,
    purple: Color,
    greyText: Color,
    textDark: Color,
    onUnlinkClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .border(
                width = 1.5.dp,
                color = Color(0xFFC2A7FF),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        val isNarrow = maxWidth < 370.dp
        val imageSize = if (isNarrow) 110.dp else 122.dp
        val titleSize = if (isNarrow) 28.sp else 30.sp
        val labelSize = if (isNarrow) 15.sp else 16.sp
        val valueSize = if (isNarrow) 16.sp else 17.sp

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.watch),
                contentDescription = "Wristband image",
                modifier = Modifier.size(imageSize)
            )

            Spacer(modifier = Modifier.width(if (isNarrow) 14.dp else 18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AppText(
                    text = if (isLinked) "Linked" else "Not Linked",
                    fontSize = titleSize,
                    fontWeight = FontWeight.Medium,
                    color = textDark,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppText(
                    text = "NFC ID:",
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    color = purple,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                AppText(
                    text = nfcId,
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    color = greyText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(7.dp))

                AppText(
                    text = "Value:",
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    color = purple,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                AppText(
                    text = profileValue,
                    fontSize = valueSize,
                    fontWeight = FontWeight.Bold,
                    color = greyText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 21.sp
                )
            }
        }

        if (isLinked) {
            AppText(
                text = "Unlink",
                fontSize = 16.sp,
                color = greyText,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                lineHeight = 20.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .clickable {
                        onUnlinkClick()
                    }
            )
        }
    }
}

@Composable
fun BottomArea(
    modifier: Modifier = Modifier,
    purple: Color,
    hintLeft: String,
    hintRight: String,
    showHintBar: Boolean,
    onAddClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        BottomNavBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            purple = purple,
            onAddClick = onAddClick
        )

        if (showHintBar) {
            BottomHintBar(
                modifier = Modifier.align(Alignment.TopCenter),
                purple = purple,
                hintLeft = hintLeft,
                hintRight = hintRight
            )
        }
    }
}

@Composable
fun BottomHintBar(
    modifier: Modifier = Modifier,
    purple: Color,
    hintLeft: String,
    hintRight: String
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(purple)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = hintLeft,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Arrow down",
                tint = Color.White,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .size(40.dp)
            )

            AppText(
                text = hintRight,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BottomNavBar(
    modifier: Modifier = Modifier,
    purple: Color,
    onAddClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFFEDE6FF))
        )

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = purple,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .size(64.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Preview(
    name = "Pixel Normal",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=412dp,height=915dp,dpi=440"
)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            isLinked = false,
            nfcId = "--",
            profileValue = "--",
            hintLeft = "Let’s Start With",
            hintRight = "A Single Tap!",
            showHintBar = true,
            onAddClick = {},
            onUnlinkClick = {}
        )
    }
}

@Preview(
    name = "Linked State",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=412dp,height=915dp,dpi=440"
)
@Composable
fun HomeScreenLinkedPreview() {
    MaterialTheme {
        HomeScreen(
            isLinked = true,
            nfcId = "2BJJ9178",
            profileValue = "UN1CO",
            hintLeft = "Let’s Start With",
            hintRight = "A Single Tap!",
            showHintBar = false,
            onAddClick = {},
            onUnlinkClick = {}
        )
    }
}
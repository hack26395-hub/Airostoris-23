package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Database and Repository layers
        val database = BookDatabase.getDatabase(applicationContext)
        val repository = BookRepository(database.bookDao())

        setContent {
            MyApplicationTheme {
                val bookViewModel: BookViewModel = viewModel(
                    factory = BookViewModel.Factory(application, repository)
                )
                MainAppScreen(bookViewModel = bookViewModel, activity = this)
            }
        }
    }
}

enum class ScreenState {
    SHELF, CREATE_NEW, READER
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScreen(bookViewModel: BookViewModel, activity: ComponentActivity) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(ScreenState.SHELF) }
    
    // Toast notification monitor
    val toastMsg by bookViewModel.toastMessage.collectAsState()
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            bookViewModel.clearToast()
        }
    }

    // Active Book selector binding
    val activeBookId by bookViewModel.activeBookId.collectAsState()
    LaunchedEffect(activeBookId) {
        if (activeBookId != null) {
            currentScreen = ScreenState.READER
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = NeonObsidian
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NeonObsidian, Color(0xFF06090F), Color(0xFF04060A))
                    )
                )
        ) {
            // Animated screen navigation
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350)) with fadeOut(animationSpec = tween(250))
                },
                label = "ScreenSwitch"
            ) { targetScreen ->
                when (targetScreen) {
                    ScreenState.SHELF -> {
                        ShelfScreen(
                            viewModel = bookViewModel,
                            onCreateNewClick = {
                                bookViewModel.resetForm()
                                currentScreen = ScreenState.CREATE_NEW
                            },
                            onBookSelected = { bookId ->
                                bookViewModel.selectBook(bookId)
                            }
                        )
                    }
                    ScreenState.CREATE_NEW -> {
                        CreateBookScreen(
                            viewModel = bookViewModel,
                            onBackClick = {
                                currentScreen = ScreenState.SHELF
                            },
                            onBookStarted = {
                                currentScreen = ScreenState.READER
                            }
                        )
                    }
                    ScreenState.READER -> {
                        ReaderScreen(
                            viewModel = bookViewModel,
                            activity = activity,
                            onBackClick = {
                                bookViewModel.clearActiveBook()
                                currentScreen = ScreenState.SHELF
                            }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// Modifier Glow Helpers
// =========================================================================
fun Modifier.neonBorder(glowColor: Color = NeonCyan) = this.border(
    width = 1.dp,
    color = glowColor.copy(alpha = 0.25f),
    shape = RoundedCornerShape(16.dp)
)

fun Modifier.sophisticatedCard(
    borderColor: Color = Color.White.copy(alpha = 0.05f),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
) = this
    .background(NeonCardSlate, shape)
    .border(width = 1.dp, color = borderColor, shape = shape)

val NeonTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    shadow = Shadow(
        color = NeonCyan.copy(alpha = 0.4f),
        offset = Offset(0f, 0f),
        blurRadius = 12f
    )
)

val NeonBtnTextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp
)

// =========================================================================
// 1. SHELF SCREEN (الرئيسية)
// =========================================================================
@Composable
fun ShelfScreen(
    viewModel: BookViewModel,
    onCreateNewClick: () -> Unit,
    onBookSelected: (Long) -> Unit
) {
    val books by viewModel.allBooks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        // App header following Sophisticated Dark theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Neon-Cyan auto_stories card
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(NeonCyan, RoundedCornerShape(12.dp))
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp), ambientColor = NeonCyan, spotColor = NeonCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📚",
                        fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                Column {
                    Text(
                        text = "AIROSTORIS",
                        style = NeonTitleStyle,
                        color = NeonCyan,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.testTag("app_brand_title")
                    )
                    Text(
                        text = "صانع الروايات الذكي المتألق",
                        color = NeonGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            
            // settings profile card
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SecondaryActionBg, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeonGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Shelf Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .neonBorder(NeonCyan),
            colors = CardDefaults.cardColors(containerColor = NeonCardSlate)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "مكتبة التأليف التوليدية",
                    color = NeonOffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "اصنع كتباً ومجلدات ساحرة خالية من علامات الترقيم المزعجة بلمسات أدبية راقية ونظام PDF يتطابق تماماً مع الصفحات الحقيقية.",
                    color = NeonGray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "مؤلفاتك (${books.size})",
                color = NeonOffWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Button(
                onClick = onCreateNewClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("create_book_fab")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "", tint = NeonCyan)
                    Text("كتاب جديدة", color = NeonOffWhite, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Books Shelf List following Sophisticated Dark theme
        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp)
                    .sophisticatedCard(borderColor = Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "📚",
                        fontSize = 50.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "لا توجد روايات في رف مذكراتك بعد",
                        color = NeonOffWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "انقر على زر 'كتاب جديدة' لصناعة أول تحفة أدبية بنظام الوميض نيون!",
                        color = NeonGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onCreateNewClick,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonObsidian),
                        modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                    ) {
                        Text("أنشئ رواية الآن", color = NeonObsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(books) { book ->
                    BookShelfCard(
                        book = book,
                        onSelect = { onBookSelected(book.id) },
                        onDelete = { viewModel.deleteBook(book.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookShelfCard(
    book: BookEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .sophisticatedCard(borderColor = NeonCyan.copy(alpha = 0.15f))
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = NeonCardSlate)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover representation inside a custom rounded container
            Box(
                modifier = Modifier
                    .size(height = 100.dp, width = 75.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1522))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!book.coverImageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = book.coverImageUri,
                        contentDescription = "Cover Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📖", fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.genre,
                            fontSize = 8.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (book.seriesName.isNotEmpty()) {
                    Text(
                        text = "${book.seriesName} (${book.seriesPart})",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = book.title,
                    color = NeonOffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الفكرة: ${book.idea}",
                    color = NeonGray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(book.genre, color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(book.language, color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "الحد الأقصى: ${book.totalPagesGoal} صفحة",
                        color = NeonGray,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف الرواية",
                    tint = NeonPink.copy(alpha = 0.8f)
                )
            }
        }
    }
}


// =========================================================================
// 2. CREATE NEW BOOK SCREEN (استمارة الصنع والتوليد)
// =========================================================================
@Composable
fun CreateBookScreen(
    viewModel: BookViewModel,
    onBackClick: () -> Unit,
    onBookStarted: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Bind VM reactive form fields
    val title by viewModel.inputTitle.collectAsState()
    val idea by viewModel.inputIdea.collectAsState()
    val language by viewModel.inputLanguage.collectAsState()
    val genre by viewModel.inputGenre.collectAsState()
    val seriesName by viewModel.inputSeriesName.collectAsState()
    val seriesPart by viewModel.inputSeriesPart.collectAsState()
    val isLiterary by viewModel.inputHasLiteraryTouches.collectAsState()
    val totalPages by viewModel.inputTotalPagesGoal.collectAsState()
    val linesPerPage by viewModel.inputLinesPerPage.collectAsState()
    val coverUri by viewModel.selectedCoverUri.collectAsState()

    val isGenerating by viewModel.isGenerating.collectAsState()
    val errorMsg by viewModel.generationError.collectAsState()

    // Activity launchers for gallery images
    val selectCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateCoverUri(it.toString()) }
    }

    val selectFirstPageImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectedPageFileUri.value = it.toString() }
    }

    if (isGenerating) {
        Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonBorder(NeonPink)
                    .background(NeonCardSlate, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                NeonPulseLoader()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        // Form Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            Text(
                text = "صانع روايتك الجديدة",
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                style = TextStyle(
                    shadow = Shadow(color = NeonCyan, blurRadius = 8f)
                )
            )
            Box(modifier = Modifier.size(40.dp)) // Spacer placeholder
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMsg != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeonPink.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, NeonPink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg!!,
                        color = NeonPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Right
                    )
                }
            }

            // Merged Title & Idea Card following Sophisticated Dark
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sophisticatedCard(borderColor = NeonCyan.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = NeonCardSlate)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "اسم الكتاب",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        TextField(
                            value = title,
                            onValueChange = { viewModel.inputTitle.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("title_textfield_input"),
                            placeholder = { Text("عنوان الرواية...", color = NeonGray, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = InputBgColor,
                                unfocusedContainerColor = InputBgColor,
                                focusedIndicatorColor = NeonCyan,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = NeonOffWhite,
                                unfocusedTextColor = NeonOffWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    Column {
                        Text(
                            text = "فكرة الكتاب",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        TextField(
                            value = idea,
                            onValueChange = { viewModel.inputIdea.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp),
                            placeholder = { Text("صف عالمك وشخصياتك والعقدة الروائية...", color = NeonGray, fontSize = 13.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = InputBgColor,
                                unfocusedContainerColor = InputBgColor,
                                focusedIndicatorColor = NeonCyan,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = NeonOffWhite,
                                unfocusedTextColor = NeonOffWhite
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Quick Selectors grid (reparsed inside custom modern cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Language card Selector
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .sophisticatedCard()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "اللغة",
                        color = NeonGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val languages = listOf("العربية", "English", "Français")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        languages.forEach { lang ->
                            val isSelected = language == lang
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.2f) else InputBgColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.inputLanguage.value = lang }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = lang,
                                        color = if (isSelected) NeonCyan else NeonOffWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Genre Card Selector
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .sophisticatedCard()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "نوع الرواية",
                        color = NeonGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val genreOptions = listOf("خيال علمي", "فانتازيا", "غموض", "رعب دافئ")
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        genreOptions.forEach { opt ->
                            val isSelected = genre == opt
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.2f) else InputBgColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.inputGenre.value = opt }
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opt,
                                        color = if (isSelected) NeonCyan else NeonOffWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Volume & Precision Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sophisticatedCard(),
                colors = CardDefaults.cardColors(containerColor = NeonCardSlate)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Target volume config
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("عدد الصفحات", color = NeonOffWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("حتى 300 صفحة", color = NeonGray, fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .background(InputBgColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$totalPages",
                                    color = NeonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Slider(
                            value = totalPages.toFloat(),
                            onValueChange = { viewModel.inputTotalPagesGoal.value = it.toInt() },
                            valueRange = 1f..300f,
                            steps = 299,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = InputBgColor
                            )
                        )
                    }

                    // Lines density config
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("أسطر الصفحة", color = NeonOffWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("كثافة النص", color = NeonGray, fontSize = 10.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .background(InputBgColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$linesPerPage",
                                    color = NeonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Slider(
                            value = linesPerPage.toFloat(),
                            onValueChange = { viewModel.inputLinesPerPage.value = it.toInt() },
                            valueRange = 5f..30f,
                            steps = 25,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = InputBgColor
                            )
                        )
                    }
                }
            }

            // Advanced Features (Lit. touches card)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sophisticatedCard()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Literary touches",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text("لمسات أدبية احترافية", color = NeonOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("صياغة لغة شعرية ساحرة ورسائل عميقة", color = NeonGray, fontSize = 10.sp)
                    }
                }
                Switch(
                    checked = isLiterary,
                    onCheckedChange = { viewModel.inputHasLiteraryTouches.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = NeonGray,
                        uncheckedTrackColor = InputBgColor
                    )
                )
            }

            // Series controller card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .sophisticatedCard()
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Series link",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "خاصية السلسلة الروائية (اختياري)",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                
                TextField(
                    value = seriesName,
                    onValueChange = { viewModel.inputSeriesName.value = it },
                    placeholder = { Text("اسم السلسة (مثال: ملحمة العوالم السبعة...)", color = NeonGray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = InputBgColor,
                        unfocusedContainerColor = InputBgColor,
                        focusedIndicatorColor = NeonCyan,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = NeonOffWhite,
                        unfocusedTextColor = NeonOffWhite
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                
                if (seriesName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("اختر الجزء الفعال:", color = NeonGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    val parts = listOf("الجزء الاول", "الجزء الثاني", "الجزء الثالث", "الجزء الرابع")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        parts.forEach { part ->
                            val isSelected = seriesPart == part
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.2f) else InputBgColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.inputSeriesPart.value = part }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(part, color = if (isSelected) NeonCyan else NeonOffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Cover attachment & First illustration triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cover box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .background(NeonCardSlate, RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .clickable { selectCoverLauncher.launch("image/*") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUri != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = "",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("تغيير الغلاف 🖼️", color = NeonOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Create, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("إضافة غلاف الرواية", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("من الاستوديو", color = NeonGray, fontSize = 9.sp)
                        }
                    }
                }

                // First page image box
                val pImgUri by viewModel.selectedPageFileUri.collectAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .background(NeonCardSlate, RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .clickable { selectFirstPageImageLauncher.launch("image/*") }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pImgUri != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = pImgUri,
                                contentDescription = "",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("تغيير صورة ص1 🎨", color = NeonOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Star, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("إضافة رسمة ص1", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("صورة داخلية أولى", color = NeonGray, fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Action Create Button & Footer styled like Sophisticated Dark bottom action bar
        Button(
            onClick = {
                viewModel.createAndStartBook { id ->
                    onBookStarted()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp), ambientColor = NeonCyan, spotColor = NeonCyan)
                .testTag("submit_creation_form_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = "",
                    tint = NeonObsidian,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "ابدأ كتابة القصة الآن",
                    color = NeonObsidian,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "* تم تحسين النصوص للتصدير المباشر بقياس صفحات PDF القياسية",
            color = TextDarkMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


// =========================================================================
// 3. READER & CONTINUATION WRITER SCREEN (لوحة القراءة والكتابة والـ PDF)
// =========================================================================
@Composable
fun ReaderScreen(
    viewModel: BookViewModel,
    activity: ComponentActivity,
    onBackClick: () -> Unit
) {
    val activeBookWithPages by viewModel.activeBookWithPages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val errorMsg by viewModel.generationError.collectAsState()

    var currentPageIndex by remember { mutableStateOf(0) }
    var isEditingContentMode by remember { mutableStateOf(false) }
    var textEditorState by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Launcher to save PDF to user chosen path
    val savePdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportActiveBookToPdf(context, outputStream)
                    outputStream.close()
                }
            } catch (e: Exception) {
                viewModel.showToast("فشل حفظ الملف: ${e.localizedMessage}")
            }
        }
    }

    // Launchers to change/add images during active read
    val changeCoverPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateCoverUri(it.toString()) }
    }

    val pageImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            val bookData = activeBookWithPages ?: return@rememberLauncherForActivityResult
            val pages = bookData.pages.sortedBy { it.pageNumber }
            if (pages.isNotEmpty() && currentPageIndex < pages.size) {
                val curPage = pages[currentPageIndex]
                viewModel.updatePageImageUri(curPage.id, pickedUri.toString())
            } else {
                viewModel.selectedPageFileUri.value = pickedUri.toString()
            }
        }
    }

    if (activeBookWithPages == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonPink)
        }
        return
    }

    val bookData = activeBookWithPages!!
    val book = bookData.book
    val pages = bookData.pages.sortedBy { it.pageNumber }

    // Make sure index is in bounds
    LaunchedEffect(pages.size) {
        if (currentPageIndex >= pages.size) {
            currentPageIndex = (pages.size - 1).coerceAtLeast(0)
        }
    }

    // Trigger dialogue for background load
    if (isGenerating) {
        Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonBorder(NeonCyan)
                    .background(NeonCardSlate, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                NeonPulseLoader()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Reader Header Panel following Sophisticated Dark
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .background(SecondaryActionBg, androidx.compose.foundation.shape.CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = book.title,
                    color = NeonOffWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.seriesName.isNotEmpty()) {
                    Text(
                        text = "${book.seriesName} (${book.seriesPart})",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // PDF Action button with stunning neon styling
            IconButton(
                onClick = {
                    val sanitizedTitle = book.title.replace("\\s+".toRegex(), "_")
                    savePdfDocumentLauncher.launch("${sanitizedTitle}_AIROSTORIS.pdf")
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(SecondaryActionBg, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    Icons.Default.Share, 
                    contentDescription = "تحويل القصة pdf", 
                    tint = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (errorMsg != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NeonPink.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, NeonPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = errorMsg!!,
                    color = NeonPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp),
                    textAlign = TextAlign.Right
                )
            }
        }

        // Active Reader Page Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .sophisticatedCard(borderColor = NeonCyan.copy(alpha = 0.15f))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Selected page header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = book.genre,
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "صفحة ${currentPageIndex + 1} من ${pages.size}",
                        color = NeonGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isArabic = book.language.contains("عرب", true) || book.language.contains("arab", true)
                // Left/Right aligned text based on chosen language
                val textAlignment = if (isArabic) TextAlign.Right else TextAlign.Left

                // Active Page Content Reader
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (pages.isEmpty()) {
                        Text(
                            text = "لا توجد أوراق مكتوبة لهذا المجلد.",
                            color = NeonGray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val activePage = pages.getOrNull(currentPageIndex)
                        if (activePage != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isArabic) Alignment.End else Alignment.Start
                            ) {
                                // Draw Page image if available
                                val pageImage = activePage.imageUri ?: viewModel.selectedPageFileUri.value
                                if (!pageImage.isNullOrEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .padding(bottom = 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = pageImage,
                                            contentDescription = "Page illustration",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }

                                if (isEditingContentMode) {
                                    TextField(
                                        value = textEditorState,
                                        onValueChange = { textEditorState = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = InputBgColor,
                                            unfocusedContainerColor = InputBgColor,
                                            focusedIndicatorColor = NeonCyan,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = NeonOffWhite,
                                            unfocusedTextColor = NeonOffWhite
                                        ),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 15.sp,
                                            lineHeight = 22.sp,
                                            textAlign = textAlignment
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                } else {
                                    Text(
                                        text = activePage.content,
                                        color = NeonOffWhite,
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        textAlign = textAlignment,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Page Options controller
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activePage = pages.getOrNull(currentPageIndex)
                    if (activePage != null) {
                        if (isEditingContentMode) {
                            Button(
                                onClick = {
                                    viewModel.editPageContent(activePage, textEditorState)
                                    isEditingContentMode = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, NeonCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("حفظ التعديل", color = NeonOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    textEditorState = activePage.content
                                    isEditingContentMode = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryActionBg),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    Text("تعديل النص", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = { pageImagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryActionBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                             ) {
                                Icon(Icons.Default.Star, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                Text("إضافة صورة للصفحة", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pagination Control & Add next page logic
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                enabled = currentPageIndex > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryActionBg,
                    disabledContainerColor = SecondaryActionBg.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, if (currentPageIndex > 0) NeonCyan.copy(alpha = 0.6f) else Color.Transparent)
            ) {
                Text("السابق", color = if (currentPageIndex > 0) NeonCyan else NeonGray, fontWeight = FontWeight.Bold)
            }

            // Central generate button
            Button(
                onClick = {
                    viewModel.selectedPageFileUri.value = null // reset prior URI to ensure clean call
                    viewModel.generateNextPageWithAI()
                    // Auto slide to the newly spawned page
                    scope.launch {
                        currentPageIndex = pages.size
                    }
                },
                enabled = pages.size < book.totalPagesGoal && !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    disabledContainerColor = SecondaryActionBg.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), ambientColor = NeonCyan, spotColor = NeonCyan)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = "", tint = NeonObsidian, modifier = Modifier.size(14.dp))
                    Text("الصفحة التالية ذكياً", color = NeonObsidian, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Button(
                onClick = { if (currentPageIndex < pages.size - 1) currentPageIndex++ },
                enabled = currentPageIndex < pages.size - 1,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryActionBg,
                    disabledContainerColor = SecondaryActionBg.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, if (currentPageIndex < pages.size - 1) NeonCyan.copy(alpha = 0.6f) else Color.Transparent)
            ) {
                Text("التالي", color = if (currentPageIndex < pages.size - 1) NeonCyan else NeonGray, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Book metadata summary shelf link
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .sophisticatedCard(),
            colors = CardDefaults.cardColors(containerColor = NeonCardSlate)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "صفحات الرواية الحالية: ${pages.size} / ${book.totalPagesGoal}",
                    color = NeonOffWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Button(
                    onClick = { changeCoverPhotoLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("أضف/غير غلاف الرواية 📷", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Simple legacy wrapper flow for standard FlowRow layout compatibility in compose
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Rely on simple Row with Horizontal scrolling if needed, or wrap elements.
    // For general modern Android layout, Row with horizontal scroll is extremely compact and behaves perfectly on compact screens.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun NeonPulseLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val rotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotate"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonPink, Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.5f * scale
                        ),
                        alpha = 0.55f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .neonBorder(NeonCyan)
                    .drawBehind {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(NeonCyan, NeonPink, NeonCyan)),
                            startAngle = rotate,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "AIROSTORIS يؤلّف فصلاً جديداً...",
            color = NeonOffWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "تجري عملية الكتابة السريعة بلمسات روائية ساحرة بدون علامات ترقيم زائدة",
            color = NeonCyan,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

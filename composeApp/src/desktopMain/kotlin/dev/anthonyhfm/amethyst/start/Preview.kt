package dev.anthonyhfm.amethyst.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import dev.anthonyhfm.amethyst.ui.theme.*

private data class PaymentRow(val id: String, val status: String, val amount: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPreview() {
    val toastState = rememberToastState()
    val sonnerState = rememberSonnerState()

    ToastProvider(state = toastState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme[colors][background])
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = "Component Catalog",
                    style = Theme[typography][h2],
                    color = Theme[colors][foreground],
                )

                // --- Grid ---
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // ========== ACCORDION ==========
                    CatalogCell("Accordion", width = 280) {
                        Accordion {
                            AccordionItem(title = "Is it accessible?") {
                                Text("Yes. It adheres to the WAI-ARIA design pattern.", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                            AccordionItem(title = "Is it styled?") {
                                Text("Yes. It uses the AmethystTheme design system.", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                            AccordionItem(title = "Is it animated?", initiallyExpanded = true) {
                                Text("Yes. It's animated by default.", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                        }
                    }

                    // ========== ALERT ==========
                    CatalogCell("Alert", width = 280) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Alert(icon = Icons.Filled.Info) {
                                Column {
                                    AlertTitle("Heads up!")
                                    AlertDescription("You can use alert to show important messages.")
                                }
                            }
                            Alert(variant = AlertVariant.Destructive, icon = Icons.Filled.Warning) {
                                Column {
                                    AlertTitle("Error")
                                    AlertDescription("Something went wrong.")
                                }
                            }
                        }
                    }

                    // ========== ALERT DIALOG ==========
                    CatalogCell("AlertDialog") {
                        val alertState = rememberDialogState()

                        Button(onClick = { alertState.visible = true }, variant = ButtonVariant.Destructive, size = ButtonSize.Small) {
                            Text("Delete Item")
                        }

                        AlertDialog(state = alertState) {
                            AlertDialogHeader {
                                AlertDialogTitle("Are you sure?")
                                AlertDialogDescription("This action cannot be undone. This will permanently delete the item.")
                            }
                            AlertDialogFooter {
                                AlertDialogCancel(onClick = { alertState.visible = false }) {
                                    Text("Cancel")
                                }
                                AlertDialogAction(
                                    onClick = { alertState.visible = false },
                                    variant = ButtonVariant.Destructive,
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }

                    // ========== ASPECT RATIO ==========
                    CatalogCell("AspectRatio") {
                        AspectRatio(ratio = 16f / 9f) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Theme[colors][muted], DefaultShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("16:9", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                        }
                    }

                    // ========== AVATAR ==========
                    CatalogCell("Avatar") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(fallbackText = "John Doe", size = 40.dp)
                            Avatar(fallbackText = "Alice B", size = 40.dp)
                            Avatar(fallbackText = "X", size = 32.dp)
                        }
                    }

                    // ========== BADGE ==========
                    CatalogCell("Badge") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            BadgeVariant.entries.forEach { variant ->
                                Badge(variant = variant) {
                                    Text(variant.name)
                                }
                            }
                        }
                    }

                    // ========== BREADCRUMB ==========
                    CatalogCell("Breadcrumb", width = 280) {
                        Breadcrumb {
                            BreadcrumbList {
                                BreadcrumbItem {
                                    BreadcrumbLink("Home", onClick = {})
                                }
                                BreadcrumbSeparator()
                                BreadcrumbItem {
                                    BreadcrumbLink("Components", onClick = {})
                                }
                                BreadcrumbSeparator()
                                BreadcrumbItem {
                                    BreadcrumbPage("Breadcrumb")
                                }
                            }
                        }
                    }

                    // ========== BUTTON — VARIANTS ==========
                    CatalogCell("Button — Variants") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ButtonVariant.entries.forEach { variant ->
                                Button(onClick = {}, variant = variant) {
                                    Text(variant.name)
                                }
                            }
                        }
                    }

                    // ========== BUTTON — SIZES ==========
                    CatalogCell("Button — Sizes") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ButtonSize.entries.forEach { size ->
                                Button(onClick = {}, size = size) {
                                    Text(size.name)
                                }
                            }
                        }
                    }

                    // ========== BUTTON — DISABLED ==========
                    CatalogCell("Button — Disabled") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {}, enabled = false) {
                                Text("Disabled Default")
                            }
                            Button(onClick = {}, variant = ButtonVariant.Destructive, enabled = false) {
                                Text("Disabled Destructive")
                            }
                        }
                    }

                    // ========== BUTTON GROUP ==========
                    CatalogCell("ButtonGroup") {
                        ButtonGroup {
                            item { Button(onClick = {}, size = ButtonSize.Small) { Text("One") } }
                            separator()
                            item { Button(onClick = {}, size = ButtonSize.Small, variant = ButtonVariant.Outline) { Text("Two") } }
                            separator()
                            item { Button(onClick = {}, size = ButtonSize.Small, variant = ButtonVariant.Outline) { Text("Three") } }
                        }
                    }

                    // ========== CALENDAR ==========
                    CatalogCell("Calendar", width = 280) {
                        val calendarState = rememberCalendarState()
                        Calendar(state = calendarState)
                    }

                    // ========== CARD ==========
                    CatalogCell("Card", width = 320) {
                        Card {
                            CardHeader {
                                CardTitle("Card Title")
                                CardDescription("This is a card description with some detail.")
                            }
                            CardContent {
                                Text("Card body content goes here.", style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                            CardFooter {
                                Button(onClick = {}, variant = ButtonVariant.Outline, size = ButtonSize.Small) { Text("Cancel") }
                                Button(onClick = {}, size = ButtonSize.Small) { Text("Save") }
                            }
                        }
                    }

                    // ========== CAROUSEL ==========
                    CatalogCell("Carousel", width = 280) {
                        Carousel(pageCount = 3) {
                            CarouselContent { page ->
                                CarouselItem {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(80.dp)
                                            .background(Theme[colors][muted], DefaultShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Slide ${page + 1}", style = Theme[typography][h4], color = Theme[colors][foreground])
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CarouselPrevious()
                                Spacer(Modifier.width(8.dp))
                                CarouselNext()
                            }
                        }
                    }

                    // ========== CHART ==========
                    CatalogCell("Chart", width = 320) {
                        val config: ChartConfig = mapOf(
                            "sales" to ChartConfigEntry("Sales", Color(0xFF2563EB)),
                        )
                        val data = listOf(
                            ChartDataPoint("Jan", mapOf("sales" to 40f)),
                            ChartDataPoint("Feb", mapOf("sales" to 65f)),
                            ChartDataPoint("Mar", mapOf("sales" to 50f)),
                            ChartDataPoint("Apr", mapOf("sales" to 80f)),
                        )
                        ChartContainer(config = config) {
                            BarChart(
                                data = data,
                                config = config,
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                            )
                        }
                    }

                    // ========== CHECKBOX ==========
                    CatalogCell("Checkbox") {
                        var c1 by remember { mutableStateOf(false) }
                        var c2 by remember { mutableStateOf(true) }
                        var c3 by remember { mutableStateOf(false) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c1, onCheckedChange = { c1 = it })
                                Label("Accept terms")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c2, onCheckedChange = { c2 = it })
                                Label("Already checked")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c3, onCheckedChange = { c3 = it }, enabled = false)
                                Label("Disabled")
                            }
                        }
                    }

                    // ========== COLLAPSIBLE ==========
                    CatalogCell("Collapsible", width = 240) {
                        Collapsible {
                            CollapsibleTrigger {
                                Button(onClick = {}, variant = ButtonVariant.Ghost, size = ButtonSize.Small) {
                                    Text("Toggle Content")
                                }
                            }
                            CollapsibleContent {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Theme[colors][muted], DefaultShape)
                                        .padding(12.dp),
                                ) {
                                    Text("Collapsible content here.", style = Theme[typography][small], color = Theme[colors][foreground])
                                }
                            }
                        }
                    }

                    // ========== COMBOBOX ==========
                    CatalogCell("Combobox", width = 240) {
                        var selected by remember { mutableStateOf("") }
                        Combobox(
                            value = selected,
                            onValueChange = { selected = it },
                            options = listOf("Kotlin", "Swift", "Rust", "Go"),
                            placeholder = "Select language…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ========== COMMAND ==========
                    CatalogCell("Command", width = 280) {
                        var query by remember { mutableStateOf("") }
                        Command {
                            CommandInput(value = query, onValueChange = { query = it }, placeholder = "Type a command…")
                            CommandList {
                                CommandGroup(heading = "Suggestions") {
                                    CommandItem(onClick = {}) { Text("Calendar") }
                                    CommandItem(onClick = {}) { Text("Search") }
                                    CommandItem(onClick = {}) { Text("Settings") }
                                }
                            }
                        }
                    }

                    // ========== CONTEXT MENU ==========
                    CatalogCell("ContextMenu") {
                        ContextMenu(
                            trigger = {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                        .background(Theme[colors][muted], DefaultShape)
                                        .border(1.dp, Theme[colors][border], DefaultShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Right click here", style = Theme[typography][small], color = Theme[colors][foreground])
                                }
                            },
                        ) {
                            ContextMenuLabel { Text("Actions") }
                            ContextMenuItem(onClick = {}) { Text("Copy") }
                            ContextMenuItem(onClick = {}) { Text("Paste") }
                            ContextMenuSeparator()
                            ContextMenuItem(onClick = {}, variant = ContextMenuItemVariant.Destructive) { Text("Delete") }
                        }
                    }

                    // ========== DATA TABLE ==========
                    CatalogCell("DataTable", width = 320) {
                        val payments = listOf(
                            PaymentRow("INV001", "Paid", "\$250.00"),
                            PaymentRow("INV002", "Pending", "\$150.00"),
                            PaymentRow("INV003", "Failed", "\$350.00"),
                        )
                        DataTable(
                            data = payments,
                            columns = listOf(
                                DataTableColumn(header = "Invoice") { Text(it.id, style = Theme[typography][small], color = Theme[colors][foreground]) },
                                DataTableColumn(header = "Status") { Badge { Text(it.status) } },
                                DataTableColumn(header = "Amount") { Text(it.amount, style = Theme[typography][small], color = Theme[colors][foreground]) },
                            ),
                            modifier = Modifier.heightIn(max = 250.dp),
                        )
                    }

                    // ========== DATE PICKER ==========
                    CatalogCell("DatePicker", width = 280) {
                        var date by remember { mutableStateOf<SimpleDate?>(null) }
                        DatePicker(
                            selectedDate = date,
                            onDateSelected = { date = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ========== DIALOG ==========
                    CatalogCell("Dialog") {
                        val dialogState = rememberDialogState()

                        Button(onClick = { dialogState.visible = true }, size = ButtonSize.Small) {
                            Text("Open Dialog")
                        }

                        Dialog(state = dialogState) {
                            DialogContent {
                                DialogHeader {
                                    DialogTitle("Edit Profile")
                                    DialogDescription("Make changes to your profile here.")
                                }
                                CardContent {
                                    var name by remember { mutableStateOf("Anthony") }
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Label("Name")
                                        Input(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                DialogFooter {
                                    Button(onClick = { dialogState.visible = false }, variant = ButtonVariant.Outline, size = ButtonSize.Small) {
                                        Text("Cancel")
                                    }
                                    Button(onClick = { dialogState.visible = false }, size = ButtonSize.Small) {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }

                    // ========== DRAWER ==========
                    CatalogCell("Drawer") {
                        val drawerState = rememberDialogState()

                        Button(onClick = { drawerState.visible = true }, size = ButtonSize.Small) {
                            Text("Open Drawer")
                        }

                        Drawer(state = drawerState) {
                            DrawerContent {
                                DrawerHeader {
                                    DrawerTitle("Drawer Title")
                                    DrawerDescription("This is a drawer description.")
                                }
                                DrawerFooter {
                                    DrawerClose(state = drawerState) { Text("Close") }
                                }
                            }
                        }
                    }

                    // ========== DROPDOWN MENU ==========
                    CatalogCell("DropdownMenu") {
                        var expanded by remember { mutableStateOf(false) }

                        DropdownMenu(
                            expanded = expanded,
                            onExpandRequest = { expanded = true },
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuTrigger(onClick = { expanded = !expanded }) {
                                Text("Open Menu ▾")
                            }
                            DropdownMenuContent(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuLabel { Text("My Account") }
                                DropdownMenuSeparator()
                                DropdownMenuItem(onClick = { expanded = false }) { Text("Profile") }
                                DropdownMenuItem(onClick = { expanded = false }) { Text("Settings") }
                                DropdownMenuSeparator()
                                DropdownMenuItem(onClick = { expanded = false }) { Text("Log out") }
                            }
                        }
                    }

                    // ========== EMPTY ==========
                    CatalogCell("Empty", width = 240) {
                        Empty {
                            EmptyIcon(Icons.Filled.Inbox)
                            EmptyTitle("No results")
                            EmptyDescription("Try adjusting your search.")
                        }
                    }

                    // ========== FIELD ==========
                    CatalogCell("Field", width = 240) {
                        var text by remember { mutableStateOf("") }
                        Field {
                            FieldLabel("Email")
                            Input(value = text, onValueChange = { text = it }, placeholder = "you@example.com", modifier = Modifier.fillMaxWidth())
                            FieldDescription("We'll never share your email.")
                        }
                    }

                    // ========== HOVER CARD ==========
                    CatalogCell("HoverCard") {
                        HoverCard(
                            trigger = {
                                Button(onClick = {}, variant = ButtonVariant.Link, size = ButtonSize.Small) {
                                    Text("@anthonyhfm")
                                }
                            },
                            content = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Anthony HFM", style = Theme[typography][h4], color = Theme[colors][foreground])
                                    Text("Creator of Amethyst", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                                }
                            },
                        )
                    }

                    // ========== INPUT ==========
                    CatalogCell("Input", width = 280) {
                        var text1 by remember { mutableStateOf("") }
                        var text2 by remember { mutableStateOf("Hello world") }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("Empty with placeholder")
                            Input(
                                value = text1,
                                onValueChange = { text1 = it },
                                placeholder = "Type something…",
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Label("With value")
                            Input(
                                value = text2,
                                onValueChange = { text2 = it },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Label("Disabled")
                            Input(
                                value = "Can't edit",
                                onValueChange = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // ========== INPUT GROUP ==========
                    CatalogCell("InputGroup", width = 280) {
                        var text by remember { mutableStateOf("") }
                        InputGroup(modifier = Modifier.fillMaxWidth()) {
                            InputGroupAddon {
                                Text("https://", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                            InputGroupInput(value = text, onValueChange = { text = it }, placeholder = "example.com")
                        }
                    }

                    // ========== INPUT OTP ==========
                    CatalogCell("InputOtp", width = 240) {
                        var otp by remember { mutableStateOf("") }
                        InputOtp(
                            value = otp,
                            onValueChange = { otp = it },
                            maxLength = 6,
                        ) {
                            InputOtpGroup {
                                for (i in 0 until 3) {
                                    InputOtpSlot(index = i)
                                }
                            }
                            InputOtpSeparator()
                            InputOtpGroup {
                                for (i in 3 until 6) {
                                    InputOtpSlot(index = i)
                                }
                            }
                        }
                    }

                    // ========== ITEM ==========
                    CatalogCell("Item", width = 240) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Item {
                                ItemIcon {
                                    Icon(Icons.Filled.Person, contentDescription = null)
                                }
                                ItemContent {
                                    ItemTitle { Text("Profile") }
                                    ItemDescription { Text("View your profile") }
                                }
                            }
                            Item {
                                ItemIcon {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                                ItemContent {
                                    ItemTitle { Text("Settings") }
                                    ItemDescription { Text("Manage preferences") }
                                }
                            }
                        }
                    }

                    // ========== KBD ==========
                    CatalogCell("Kbd") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Kbd("⌘")
                            Kbd("K")
                        }
                    }

                    // ========== LABEL ==========
                    CatalogCell("Label") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Label("Email")
                            Label("Password")
                            Label("Username")
                        }
                    }

                    // ========== MENUBAR ==========
                    CatalogCell("Menubar", width = 320) {
                        Menubar {
                            var fileExpanded by remember { mutableStateOf(false) }
                            MenubarMenu {
                                MenubarTrigger(onClick = { fileExpanded = !fileExpanded }) { Text("File") }
                                MenubarContent(expanded = fileExpanded, onDismissRequest = { fileExpanded = false }) {
                                    MenubarItem(onClick = { fileExpanded = false }) { Text("New") }
                                    MenubarItem(onClick = { fileExpanded = false }) { Text("Open") }
                                    MenubarSeparator()
                                    MenubarItem(onClick = { fileExpanded = false }) { Text("Exit") }
                                }
                            }
                            var editExpanded by remember { mutableStateOf(false) }
                            MenubarMenu {
                                MenubarTrigger(onClick = { editExpanded = !editExpanded }) { Text("Edit") }
                                MenubarContent(expanded = editExpanded, onDismissRequest = { editExpanded = false }) {
                                    MenubarItem(onClick = { editExpanded = false }) { Text("Undo") }
                                    MenubarItem(onClick = { editExpanded = false }) { Text("Redo") }
                                }
                            }
                        }
                    }

                    // ========== NATIVE SELECT ==========
                    CatalogCell("NativeSelect", width = 240) {
                        var selected by remember { mutableStateOf("") }
                        NativeSelect(
                            value = selected,
                            onValueChange = { selected = it },
                            options = listOf(
                                NativeSelectOption("react", "React"),
                                NativeSelectOption("vue", "Vue"),
                                NativeSelectOption("svelte", "Svelte"),
                            ),
                            placeholder = "Select framework…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ========== NAVIGATION MENU ==========
                    CatalogCell("NavigationMenu", width = 320) {
                        NavigationMenu {
                            NavigationMenuList {
                                NavigationMenuItem {
                                    NavigationMenuLink(onClick = {}, active = true) { Text("Home") }
                                }
                                NavigationMenuItem {
                                    NavigationMenuLink(onClick = {}) { Text("About") }
                                }
                                NavigationMenuItem {
                                    NavigationMenuLink(onClick = {}) { Text("Contact") }
                                }
                            }
                        }
                    }

                    // ========== PAGINATION ==========
                    CatalogCell("Pagination", width = 320) {
                        var currentPage by remember { mutableStateOf(2) }
                        Pagination {
                            PaginationContent {
                                PaginationPrevious(onClick = { if (currentPage > 1) currentPage-- })
                                PaginationItem {
                                    PaginationLink(page = 1, isActive = currentPage == 1, onClick = { currentPage = 1 })
                                }
                                PaginationItem {
                                    PaginationLink(page = 2, isActive = currentPage == 2, onClick = { currentPage = 2 })
                                }
                                PaginationItem {
                                    PaginationLink(page = 3, isActive = currentPage == 3, onClick = { currentPage = 3 })
                                }
                                PaginationNext(onClick = { if (currentPage < 3) currentPage++ })
                            }
                        }
                    }

                    // ========== POPOVER ==========
                    CatalogCell("Popover") {
                        var expanded by remember { mutableStateOf(false) }

                        Popover(
                            expanded = expanded,
                            onExpandRequest = { expanded = true },
                            onDismissRequest = { expanded = false },
                            trigger = {
                                Button(onClick = { expanded = !expanded }, variant = ButtonVariant.Outline, size = ButtonSize.Small) {
                                    Text("Open Popover")
                                }
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Popover Content", style = Theme[typography][h4], color = Theme[colors][foreground])
                                Text("Place any content here.", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                            }
                        }
                    }

                    // ========== PROGRESS ==========
                    CatalogCell("Progress", width = 240) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("60%")
                            Progress(value = 0.6f, modifier = Modifier.fillMaxWidth())
                            Label("30%")
                            Progress(value = 0.3f, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // ========== RADIO GROUP ==========
                    CatalogCell("RadioGroup") {
                        var selected by remember { mutableStateOf("option1") }

                        RadioGroup(
                            value = selected,
                            onValueChange = { selected = it },
                        ) {
                            RadioGroupItem(value = "option1", label = "Option 1")
                            RadioGroupItem(value = "option2", label = "Option 2")
                            RadioGroupItem(value = "option3", label = "Option 3 (disabled)", enabled = false)
                        }
                    }

                    // ========== RESIZABLE ==========
                    CatalogCell("Resizable", width = 280) {
                        val resizableState = rememberResizablePanelGroupState(0.5f, 0.5f)
                        ResizablePanelGroup(
                            state = resizableState,
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                        ) {
                            ResizablePanel {
                                Box(Modifier.fillMaxSize().background(Theme[colors][muted], DefaultShape), contentAlignment = Alignment.Center) {
                                    Text("Panel 1", style = Theme[typography][small], color = Theme[colors][foreground])
                                }
                            }
                            ResizableHandle()
                            ResizablePanel {
                                Box(Modifier.fillMaxSize().background(Theme[colors][muted], DefaultShape), contentAlignment = Alignment.Center) {
                                    Text("Panel 2", style = Theme[typography][small], color = Theme[colors][foreground])
                                }
                            }
                        }
                    }

                    // ========== SCROLL AREA ==========
                    CatalogCell("ScrollArea") {
                        ScrollArea(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(20) {
                                    Text("Item ${it + 1}", style = Theme[typography][small], color = Theme[colors][foreground])
                                }
                            }
                        }
                    }

                    // ========== SELECT ==========
                    CatalogCell("Select", width = 240) {
                        var selected by remember { mutableStateOf("") }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("Framework")
                            Select(
                                value = selected,
                                onValueChange = { selected = it },
                                options = listOf("Compose", "SwiftUI", "Flutter", "React Native"),
                                placeholder = "Select a framework…",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // ========== SEPARATOR ==========
                    CatalogCell("Separator") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Above", style = Theme[typography][small], color = Theme[colors][foreground])
                            Separator()
                            Text("Below", style = Theme[typography][small], color = Theme[colors][foreground])

                            Spacer(Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Left", style = Theme[typography][small], color = Theme[colors][foreground])
                                Separator(orientation = SeparatorOrientation.Vertical)
                                Text("Right", style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                        }
                    }

                    // ========== SHEET ==========
                    CatalogCell("Sheet") {
                        val sheetState = rememberDialogState()

                        Button(onClick = { sheetState.visible = true }, size = ButtonSize.Small, variant = ButtonVariant.Outline) {
                            Text("Open Sheet")
                        }

                        Sheet(state = sheetState) {
                            SheetContent {
                                SheetHeader {
                                    SheetTitle("Sheet Title")
                                    SheetDescription("Make changes here.")
                                }
                                SheetFooter {
                                    Button(onClick = { sheetState.visible = false }, size = ButtonSize.Small) {
                                        Text("Save Changes")
                                    }
                                }
                            }
                        }
                    }

                    // ========== SIDEBAR ==========
                    CatalogCell("Sidebar", width = 280) {
                        val sidebarState = rememberSidebarState()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { sidebarState.toggle() }, size = ButtonSize.Small, variant = ButtonVariant.Outline) {
                                Text(if (sidebarState.expanded) "Collapse" else "Expand")
                            }
                            SidebarProvider(state = sidebarState) {
                                Sidebar(state = sidebarState) {
                                    SidebarContent {
                                        SidebarGroup {
                                            SidebarGroupLabel("Menu")
                                            SidebarGroupContent {
                                                SidebarMenu {
                                                    SidebarMenuItem {
                                                        SidebarMenuButton(onClick = {}) { Text("Home") }
                                                    }
                                                    SidebarMenuItem {
                                                        SidebarMenuButton(onClick = {}) { Text("Settings") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ========== SKELETON ==========
                    CatalogCell("Skeleton") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Skeleton(modifier = Modifier.fillMaxWidth().height(16.dp))
                            Skeleton(modifier = Modifier.fillMaxWidth(0.8f).height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Skeleton(modifier = Modifier.size(40.dp), shape = FullShape)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Skeleton(modifier = Modifier.width(120.dp).height(12.dp))
                                    Skeleton(modifier = Modifier.width(80.dp).height(12.dp))
                                }
                            }
                        }
                    }

                    // ========== SLIDER ==========
                    CatalogCell("Slider", width = 240) {
                        var value by remember { mutableStateOf(0.5f) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Slider(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth())
                            Text("Value: ${(value * 100).toInt()}%", style = Theme[typography][small], color = Theme[colors][mutedForeground])
                        }
                    }

                    // ========== SONNER ==========
                    CatalogCell("Sonner") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { sonnerState.success("Success", "Operation completed.") }, size = ButtonSize.Small) { Text("Success") }
                            Button(onClick = { sonnerState.error("Error", "Something failed.") }, size = ButtonSize.Small, variant = ButtonVariant.Destructive) { Text("Error") }
                            Button(onClick = { sonnerState.info("Info", "Just FYI.") }, size = ButtonSize.Small, variant = ButtonVariant.Outline) { Text("Info") }
                        }
                    }

                    // ========== SPINNER ==========
                    CatalogCell("Spinner") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spinner(size = 16.dp)
                            Spinner(size = 24.dp)
                            Spinner(size = 32.dp)
                        }
                    }

                    // ========== SWITCH ==========
                    CatalogCell("Switch") {
                        var s1 by remember { mutableStateOf(false) }
                        var s2 by remember { mutableStateOf(true) }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = s1, onCheckedChange = { s1 = it })
                                Label("Airplane Mode")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = s2, onCheckedChange = { s2 = it })
                                Label("Wi-Fi")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = false, onCheckedChange = {}, enabled = false)
                                Label("Disabled")
                            }
                        }
                    }

                    // ========== TABLE ==========
                    // Table primitives (Table, TableHeader, TableBody, TableRow, TableHead,
                    // TableCell) share signatures with DataTable internals. Demonstrated
                    // via DataTable above; raw primitives can be used with explicit imports.
                    CatalogCell("Table", width = 320) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().background(Theme[colors][muted]).padding(8.dp)) {
                                Text("Name", modifier = Modifier.weight(1f), style = Theme[typography][small].copy(fontWeight = FontWeight.Bold), color = Theme[colors][foreground])
                                Text("Role", modifier = Modifier.weight(1f), style = Theme[typography][small].copy(fontWeight = FontWeight.Bold), color = Theme[colors][foreground])
                            }
                            Separator()
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text("Alice", modifier = Modifier.weight(1f), style = Theme[typography][small], color = Theme[colors][foreground])
                                Text("Developer", modifier = Modifier.weight(1f), style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                            Separator()
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text("Bob", modifier = Modifier.weight(1f), style = Theme[typography][small], color = Theme[colors][foreground])
                                Text("Designer", modifier = Modifier.weight(1f), style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                        }
                    }

                    // ========== TABS ==========
                    CatalogCell("Tabs", width = 320) {
                        var selectedTab by remember { mutableStateOf("account") }
                        val tabKeys = listOf("account", "password", "settings")

                        Tabs(selectedTab = selectedTab, tabs = tabKeys) {
                            TabsList {
                                tabKeys.forEach { key ->
                                    TabsTrigger(
                                        key = key,
                                        selected = selectedTab == key,
                                        onSelected = { selectedTab = key },
                                    ) {
                                        Text(key.replaceFirstChar { it.uppercase() })
                                    }
                                }
                            }

                            TabsContent(key = "account") {
                                Text("Account settings content.", style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                            TabsContent(key = "password") {
                                Text("Password settings content.", style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                            TabsContent(key = "settings") {
                                Text("General settings content.", style = Theme[typography][small], color = Theme[colors][foreground])
                            }
                        }
                    }

                    // ========== TEXTAREA ==========
                    CatalogCell("Textarea", width = 280) {
                        var text by remember { mutableStateOf("") }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("Message")
                            Textarea(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = "Enter your message…",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // ========== TOAST ==========
                    CatalogCell("Toast") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { toastState.show("Event created", description = "Friday, March 13, 2026 at 5:57 PM") },
                                size = ButtonSize.Small,
                            ) {
                                Text("Show Toast")
                            }
                            Button(
                                onClick = { toastState.show("Error", description = "Something went wrong.", variant = ToastVariant.Destructive) },
                                variant = ButtonVariant.Destructive,
                                size = ButtonSize.Small,
                            ) {
                                Text("Destructive Toast")
                            }
                        }
                    }

                    // ========== TOGGLE ==========
                    CatalogCell("Toggle") {
                        var bold by remember { mutableStateOf(false) }
                        var italic by remember { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Toggle(pressed = bold, onPressedChange = { bold = it }) {
                                Text("B", style = Theme[typography][small].copy(fontWeight = FontWeight.Bold))
                            }
                            Toggle(pressed = italic, onPressedChange = { italic = it }, variant = ToggleVariant.Outline) {
                                Text("I", style = Theme[typography][small].copy(fontStyle = FontStyle.Italic))
                            }
                        }
                    }

                    // ========== TOGGLE GROUP ==========
                    CatalogCell("ToggleGroup") {
                        var selected by remember { mutableStateOf("left") }
                        ToggleGroup(value = selected, onValueChange = { selected = it }) {
                            ToggleGroupItem(value = "left") { Text("Left") }
                            ToggleGroupItem(value = "center") { Text("Center") }
                            ToggleGroupItem(value = "right") { Text("Right") }
                        }
                    }

                    // ========== TOOLTIP ==========
                    CatalogCell("Tooltip") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tooltip(text = "This is a tooltip") {
                                Button(onClick = {}, variant = ButtonVariant.Outline, size = ButtonSize.Small) {
                                    Text("Hover me")
                                }
                            }
                        }
                    }

                    // ========== TYPOGRAPHY ==========
                    CatalogCell("Typography", width = 280) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TypographyH1("Heading 1")
                            TypographyH2("Heading 2")
                            TypographyH3("Heading 3")
                            TypographyH4("Heading 4")
                            TypographyP("Paragraph text")
                            TypographyLead("Lead text")
                            TypographyLarge("Large text")
                            TypographySmall("Small text")
                            TypographyMuted("Muted text")
                            TypographyInlineCode("inline code")
                            TypographyBlockquote("Blockquote")
                        }
                    }
                }
            }

            // Sonner toaster overlay
            Toaster(state = sonnerState)
        }
    }
}

@Composable
private fun CatalogCell(
    title: String,
    width: Int = 200,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Theme[colors][border], RoundedCornerShape(8.dp))
            .background(Theme[colors][card])
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = Theme[typography][h4],
            color = Theme[colors][foreground],
        )
        content()
    }
}

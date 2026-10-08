//
//  ContentView.swift
//  iosApp
//
//  Created by Anthony Hofmeister on 30.10.25.
//  Copyright © 2025 Anthony Hofmeister. All rights reserved.
//

import UIKit
import SwiftUI
import ComposeApp

// MARK: - Workspace host (KMP Compose)

private class OrientationContainerViewController: UIViewController {
    let childViewController: UIViewController
    var forcedLandscape: Bool = false

    init(child: UIViewController) {
        self.childViewController = child
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(childViewController)
        childViewController.view.frame = view.bounds
        childViewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(childViewController.view)
        childViewController.didMove(toParent: self)
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .portrait
    }

    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        return .portrait
    }
}

private struct WorkspaceView: UIViewControllerRepresentable {
    let darkMode: Bool
    let onBack: () -> Void

    func makeUIViewController(context: Context) -> OrientationContainerViewController {
        let child = MainViewControllerKt.WorkspaceViewController(darkMode: darkMode, onBack: onBack)
        let container = OrientationContainerViewController(child: child)
        
        IosWorkspaceBridge.shared.onOrientationChanged = { [weak container] landscape in
            DispatchQueue.main.async {
                container?.forcedLandscape = landscape.boolValue
            }
        }
        
        return container
    }

    func updateUIViewController(_ uiViewController: OrientationContainerViewController, context: Context) {}
}

private struct LaunchpadPreviewView: UIViewControllerRepresentable {
    let index: Int
    let darkMode: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = IosLaunchpadPickerKt.launchpadPreviewViewController(
            index: Int32(index),
            darkMode: darkMode
        )
        controller.view.isUserInteractionEnabled = false
        controller.view.isOpaque = false
        controller.view.backgroundColor = .clear
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private struct LaunchpadPickerSheet: View {
    private enum Category: String, CaseIterable {
        case novation = "Novation"
        case other = "Other"
    }

    private struct Option: Identifiable {
        let id: Int
        let name: String
        let detail: String
        let category: Category
    }

    private let options: [Option] = [
        Option(id: 0, name: "Launchpad Pro", detail: "Classic performance layout", category: .novation),
        Option(id: 1, name: "Launchpad X", detail: "Compact grid with side controls", category: .novation),
        Option(id: 2, name: "Launchpad Pro MK3", detail: "Expanded performance controls", category: .novation),
        Option(id: 3, name: "Launchpad MK2", detail: "Classic Launchpad grid", category: .novation),
        Option(id: 4, name: "Idealised", detail: "A clean virtual grid", category: .novation),
        Option(id: 5, name: "Mystrix", detail: "Alternative controller layout", category: .other),
        Option(id: 6, name: "Midi Fighter 64", detail: "A focused 8 × 8 grid", category: .other)
    ]

    let darkMode: Bool
    let onDismiss: () -> Void

    @State private var category: Category = .novation
    @State private var selectedIndex = 0
    private let isReplacing = IosLaunchpadPickerKt.isReplacingLaunchpadFromIosPicker()

    private var visibleOptions: [Option] {
        options.filter { $0.category == category }
    }

    private var theme: AmethystTheme {
        AmethystTheme(darkMode: darkMode)
    }

    private func previewSize(in geometry: GeometryProxy) -> CGFloat {
        max(80, min(geometry.size.width - 48, geometry.size.height - 245))
    }

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                VStack(spacing: 8) {
                    Text(isReplacing ? "Choose a replacement layout. Bindings stay with this device." : "Choose a Launchpad for this workspace")
                        .font(.subheadline)
                        .foregroundStyle(theme.mutedForeground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)

                    Picker("Device family", selection: $category) {
                        ForEach(Category.allCases, id: \.self) { category in
                            Text(category.rawValue).tag(category)
                        }
                    }
                    .pickerStyle(.segmented)
                    .tint(theme.primary)
                    .onChange(of: category) { _, newCategory in
                        selectedIndex = options.first { $0.category == newCategory }?.id ?? 0
                    }

                    TabView(selection: $selectedIndex) {
                        ForEach(visibleOptions) { option in
                            LaunchpadPreviewView(index: option.id, darkMode: darkMode)
                                .frame(
                                    width: previewSize(in: geometry),
                                    height: previewSize(in: geometry)
                                )
                                .frame(maxWidth: .infinity)
                                .tag(option.id)
                                .accessibilityLabel(option.name)
                        }
                    }
                    .frame(height: previewSize(in: geometry))
                    .tabViewStyle(.page(indexDisplayMode: .never))

                    if let selectedOption = options.first(where: { $0.id == selectedIndex }) {
                        VStack(spacing: 3) {
                            Text(selectedOption.name)
                                .font(.headline)
                                .foregroundStyle(theme.foreground)
                            Text(selectedOption.detail)
                                .font(.subheadline)
                                .foregroundStyle(theme.mutedForeground)
                        }
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .accessibilityElement(children: .combine)
                    }

                    HStack(spacing: 0) {
                        ForEach(visibleOptions) { option in
                            Button {
                                selectedIndex = option.id
                            } label: {
                                Circle()
                                    .fill(option.id == selectedIndex ? theme.primary : theme.mutedForeground.opacity(0.45))
                                    .frame(width: option.id == selectedIndex ? 9 : 7, height: option.id == selectedIndex ? 9 : 7)
                                    .frame(width: 44, height: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(option.name)
                            .accessibilityAddTraits(option.id == selectedIndex ? .isSelected : [])
                        }
                    }
                    .frame(maxWidth: .infinity)
                    Button {
                        IosLaunchpadPickerKt.addVirtualLaunchpadFromIosPicker(index: Int32(selectedIndex))
                        onDismiss()
                    } label: {
                        Label(isReplacing ? "Swap model" : "Add to Workspace", systemImage: isReplacing ? "arrow.left.arrow.right" : "plus")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 50)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(theme.primaryForeground)
                    .background(theme.primary, in: Capsule())
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .background(theme.background.ignoresSafeArea())
            .navigationTitle(isReplacing ? "Swap Launchpad model" : "Add device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if #available(iOS 26.0, *) {
                        Button(role: .close, action: onDismiss)
                    } else {
                        Button(action: onDismiss) {
                            Image(systemName: "xmark")
                        }
                    }
                }
            }
        }
        .presentationDetents([.fraction(0.72)])
        .presentationDragIndicator(.hidden)
        .presentationBackground(theme.background)
    }
}

private struct DeviceConfigurationSheet: View {
    let uuid: String
    let darkMode: Bool
    let onDismiss: () -> Void

    @State private var devices: [IosMidiDeviceOption] = []
    @State private var selectedId: String?

    private var theme: AmethystTheme {
        AmethystTheme(darkMode: darkMode)
    }

    private var selectedName: String {
        if devices.isEmpty {
            return "No devices available"
        }

        return devices.first(where: { $0.id == selectedId })?.name ?? "Automatic"
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                Text("Choose the MIDI device for this layout element.")
                    .font(.subheadline)
                    .foregroundStyle(theme.mutedForeground)

                Text("MIDI Device")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.foreground)

                Menu {
                    Button("Automatic") {
                        selectedId = nil
                    }

                    ForEach(devices, id: \.id) { device in
                        Button(device.name) {
                            selectedId = device.id
                        }
                    }
                } label: {
                    HStack {
                        Text(selectedName)
                        Spacer()
                        Image(systemName: "chevron.up.chevron.down")
                    }
                    .font(.subheadline)
                    .foregroundStyle(devices.isEmpty ? theme.mutedForeground : theme.foreground)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .padding(.horizontal, 16)
                    .background(theme.secondary, in: RoundedRectangle(cornerRadius: 12))
                    .contentShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(devices.isEmpty)

                Text("Launchpad devices are detected automatically.")
                    .font(.footnote)
                    .foregroundStyle(theme.mutedForeground)

                Button {
                    IosDeviceConfigurationKt.iosSaveMidiDeviceConfiguration(
                        uuid: uuid,
                        deviceId: selectedId
                    )
                    onDismiss()
                } label: {
                    Text("Save")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 50)
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .foregroundStyle(theme.primaryForeground)
                .background(theme.primary, in: Capsule())
                .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Device Configuration")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if #available(iOS 26.0, *) {
                        Button(role: .close, action: onDismiss)
                    } else {
                        Button(action: onDismiss) {
                            Image(systemName: "xmark")
                        }
                    }
                }
            }
            .task {
                selectedId = IosDeviceConfigurationKt.iosConfiguredMidiDeviceId(uuid: uuid)

                while !Task.isCancelled {
                    devices = IosDeviceConfigurationKt.iosMidiDeviceOptions()
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                }
            }
        }
        .presentationDetents([.height(310)])
        .presentationDragIndicator(.hidden)
        .presentationBackground(theme.background)
    }
}

// MARK: - Root content

struct ContentView: View {
    private struct DeviceStyleTarget: Identifiable {
        let id: String
    }

    private enum HomeTab: Hashable {
        case projects
        case browser
        case arcade
        case profile
    }

    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme)  private var colorScheme

    @State private var viewModel = HomeViewModel()
    @State private var settingsViewModel = SettingsViewModel()
    @State private var accountViewModel: AccountViewModel
    @State private var hubFeedViewModel: HubFeedViewModel
    @State private var hubSearchViewModel: HubSearchViewModel
    @State private var localization = AppLocalization()
    @State private var profileTabAvatar: UIImage?
    @State private var showSettingsSheet = false
    @State private var showDevicePickerSheet = false
    @State private var showDeviceConfigurationSheet = false
    @State private var configuringDeviceId = ""
    @State private var deviceStyleTarget: DeviceStyleTarget?
    @State private var showSplashScreen = true
    @State private var selectedHomeTab: HomeTab = .projects
    @State private var hubSearchText = ""

    init() {
        let accountViewModel = AccountViewModel()
        _accountViewModel = State(initialValue: accountViewModel)
        _hubFeedViewModel = State(
            initialValue: HubFeedViewModel(repository: accountViewModel.repository)
        )
        _hubSearchViewModel = State(
            initialValue: HubSearchViewModel(repository: accountViewModel.repository)
        )
    }

    private var theme: AmethystTheme {
        AmethystTheme(darkMode: colorScheme == .dark)
    }

    var body: some View {
        ZStack {
            Group {
                if viewModel.isWorkspaceOpen {
                    WorkspaceView(darkMode: colorScheme == .dark) {
                        viewModel.workspaceClosed()
                    }
                    .ignoresSafeArea()
                    .onAppear {
                        IosWorkspaceBridge.shared.onShowSettings = {
                            showSettingsSheet = true
                        }
                        IosWorkspaceBridge.shared.onShowDevicePicker = {
                            showDevicePickerSheet = true
                        }
                        IosWorkspaceBridge.shared.onShowDeviceConfigurator = { uuid in
                            configuringDeviceId = uuid
                            showDeviceConfigurationSheet = true
                        }
                        IosWorkspaceBridge.shared.onShowDeviceStyle = { uuid in
                            deviceStyleTarget = DeviceStyleTarget(id: uuid)
                        }
                        IosWorkspaceBridge.shared.createLiquidGlassEffect = {
                            if #available(iOS 26.0, *) {
                                let effect = UIGlassEffect(style: .regular)
                                effect.isInteractive = true
                                return effect
                            } else {
                                return UIBlurEffect(style: .systemThinMaterial)
                            }
                        }
                        if #available(iOS 26.0, *) {
                            IosWorkspaceBridge.shared.createLiquidGlassContainerEffect = {
                                let effect = UIGlassContainerEffect()
                                effect.spacing = 10
                                return effect
                            }
                        } else {
                            IosWorkspaceBridge.shared.createLiquidGlassContainerEffect = nil
                        }
                        IosWorkspaceBridge.shared.createLiquidGlassButtonConfiguration = {
                            if #available(iOS 26.0, *) {
                                var configuration = UIButton.Configuration.glass()
                                configuration.cornerStyle = .capsule
                                configuration.indicator = .none
                                return configuration._bridgeToObjectiveC()
                            } else {
                                var configuration = UIButton.Configuration.bordered()
                                configuration.cornerStyle = .capsule
                                return configuration._bridgeToObjectiveC()
                            }
                        }
                    }
                    .onDisappear {
                        IosWorkspaceBridge.shared.onOrientationChanged = nil
                        IosWorkspaceBridge.shared.onShowSettings = nil
                        IosWorkspaceBridge.shared.onShowDevicePicker = nil
                        IosWorkspaceBridge.shared.onShowDeviceConfigurator = nil
                        IosWorkspaceBridge.shared.onShowDeviceStyle = nil
                    }
                    .alert("Amethyst", isPresented: Binding(
                        get: { viewModel.errorMessage != nil },
                        set: { if !$0 { viewModel.errorMessage = nil } }
                    )) {
                        Button("OK", role: .cancel) { viewModel.errorMessage = nil }
                    } message: {
                        Text(viewModel.errorMessage ?? "")
                    }
                    .sheet(isPresented: $showSettingsSheet) {
                        SettingsTabView(
                            viewModel: settingsViewModel,
                            accountViewModel: accountViewModel,
                            showsCloseButton: true
                        )
                    }
                    .sheet(isPresented: $showDevicePickerSheet) {
                        LaunchpadPickerSheet(darkMode: colorScheme == .dark) {
                            showDevicePickerSheet = false
                        }
                        .onDisappear {
                            IosLaunchpadPickerKt.dismissLaunchpadFromIosPicker()
                        }
                    }
                    .sheet(isPresented: $showDeviceConfigurationSheet) {
                        DeviceConfigurationSheet(
                            uuid: configuringDeviceId,
                            darkMode: colorScheme == .dark
                        ) {
                            showDeviceConfigurationSheet = false
                        }
                    }
                    .sheet(item: $deviceStyleTarget) { target in
                        DeviceStyleSheet(
                            uuid: target.id,
                            darkMode: colorScheme == .dark
                        ) {
                            deviceStyleTarget = nil
                        }
                    }
                } else {
                    homeTabView
                }
            }

            if viewModel.isLoading {
                LoadingScreenView(
                    progress: viewModel.loadingProgress,
                    title: viewModel.loadingTitle,
                    statusText: viewModel.loadingStatusText,
                    detailText: viewModel.loadingDetailText
                )
                .ignoresSafeArea()
                .transition(.opacity)
            }

            if showSplashScreen {
                SplashScreenView {
                    showSplashScreen = false
                }
                .ignoresSafeArea()
                .transition(.opacity)
            }
        }
        .environment(localization)
        .environment(\.locale, Locale(identifier: localization.languageTag))
        .animation(.easeInOut(duration: 0.25), value: viewModel.isLoading)
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                UIApplication.shared.isIdleTimerDisabled = true
            }
        }
        .alert(
            localization.string("workspace_exit_dialog_title", fallback: "Unsaved Changes"),
            isPresented: $viewModel.showsHubWorkspaceChangeAlert
        ) {
            Button(localization.string("workspace_exit_dialog_save", fallback: "Save")) {
                viewModel.saveAndOpenPendingHubProject()
            }
            Button(localization.string("workspace_exit_dialog_dont_save", fallback: "Don't Save"), role: .destructive) {
                viewModel.discardAndOpenPendingHubProject()
            }
            Button(localization.string("workspace_exit_dialog_cancel", fallback: "Cancel"), role: .cancel) {
                viewModel.cancelPendingHubProjects()
            }
        } message: {
            Text(localization.string("workspace_exit_dialog_description", fallback: "Do you want to save your changes before opening another project?"))
        }
        .onOpenURL { url in
            handleIncomingURL(url)
        }
    }

    private func handleIncomingURL(_ url: URL) {
        if url.scheme?.caseInsensitiveCompare("amethyst") == .orderedSame {
            guard let link = HubDeepLinks.shared.parse(value: url.absoluteString) else { return }
            selectedHomeTab = .projects
            showSettingsSheet = false
            showDevicePickerSheet = false
            showDeviceConfigurationSheet = false
            deviceStyleTarget = nil
            viewModel.openHubProject(link: link, repository: accountViewModel.repository)
            return
        }

        viewModel.openFile(url: url)
    }

    // MARK: - Home tab bar

    private var homeTabView: some View {
        TabView(selection: $selectedHomeTab) {
            ProjectsTabView(
                viewModel: viewModel,
                repository: accountViewModel.repository,
                onShowProfile: { selectedHomeTab = .profile }
            )
                .tag(HomeTab.projects)
                .tabItem {
                    Label(localization.string("home_nav_tab_projects", fallback: "Projects"), systemImage: "folder")
                }

            HubTabView(
                viewModel: hubFeedViewModel,
                searchViewModel: hubSearchViewModel,
                searchText: $hubSearchText,
                sessionRevision: accountViewModel.sessionRevision,
                onShowProfile: { selectedHomeTab = .profile },
                onOpenDownloadedFile: { url, projectID, title in
                    viewModel.openDownloadedFile(url: url, projectID: projectID, title: title)
                }
            )
            .tag(HomeTab.browser)
            .tabItem {
                Label(localization.string("home_nav_tab_browser", fallback: "Hub"), systemImage: "globe")
            }

            NavigationStack {
                ZStack {
                    theme.background.ignoresSafeArea()
                    VStack(spacing: 18) {
                        Image(systemName: "gamecontroller")
                            .font(.system(size: 44))
                            .foregroundStyle(theme.primary)
                        Text(localization.string("home_arcade_coming_title", fallback: "Amethyst Arcade"))
                            .font(.title.bold())
                            .foregroundStyle(theme.foreground)
                        Text(localization.string("home_arcade_coming_description", fallback: "Playable beatmaps, score systems, and interactive rhythm challenges are coming to Amethyst soon!"))
                            .font(.body)
                            .foregroundStyle(theme.mutedForeground)
                            .multilineTextAlignment(.center)
                        Button(localization.string("home_arcade_explore_hub", fallback: "Explore Hub")) {
                            selectedHomeTab = .browser
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(theme.primary)
                        .controlSize(.large)
                        .padding(.top, 8)
                    }
                    .frame(maxWidth: 360)
                    .padding(.horizontal, 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .navigationTitle(localization.string("home_arcade_title", fallback: "Arcade"))
                }
            }
            .tint(theme.glassForeground)
            .tag(HomeTab.arcade)
            .tabItem {
                Label(localization.string("home_nav_tab_arcade", fallback: "Arcade"), systemImage: "gamecontroller")
            }

            SettingsTabView(
                viewModel: settingsViewModel,
                accountViewModel: accountViewModel
            )
                .tag(HomeTab.profile)
                .tabItem {
                    Label {
                        Text(localization.string("profile_title", fallback: "Profile"))
                    } icon: {
                        if let profileTabAvatar {
                            Image(uiImage: profileTabAvatar)
                                .renderingMode(.original)
                        } else {
                            Image(systemName: "person.crop.circle")
                        }
                    }
                }
        }
        .tint(theme.primary)
        .toolbarBackground(theme.glassSurface, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .amethystThemed()
        .task(id: accountViewModel.resolvedAvatarURL) {
            await loadProfileTabAvatar()
        }
    }

    @MainActor
    private func loadProfileTabAvatar() async {
        guard let avatarURL = accountViewModel.resolvedAvatarURL else {
            profileTabAvatar = nil
            return
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: avatarURL)
            guard !Task.isCancelled, let image = UIImage(data: data) else { return }
            profileTabAvatar = image.circularTabBarIcon()
        } catch {
            guard !Task.isCancelled else { return }
            profileTabAvatar = nil
        }
    }
}

private extension UIImage {
    func circularTabBarIcon(diameter: CGFloat = 26) -> UIImage {
        let size = CGSize(width: diameter, height: diameter)
        let renderer = UIGraphicsImageRenderer(size: size)

        return renderer.image { _ in
            let bounds = CGRect(origin: .zero, size: size)
            UIBezierPath(ovalIn: bounds).addClip()

            let scale = max(diameter / self.size.width, diameter / self.size.height)
            let drawSize = CGSize(width: self.size.width * scale, height: self.size.height * scale)
            let drawRect = CGRect(
                x: (diameter - drawSize.width) / 2,
                y: (diameter - drawSize.height) / 2,
                width: drawSize.width,
                height: drawSize.height
            )
            draw(in: drawRect)
        }
        .withRenderingMode(.alwaysOriginal)
    }
}

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef AppDir
  #define AppDir "..\..\composeApp\build\compose\binaries\main\app\Nocturne"
#endif
#ifndef OutDir
  #define OutDir "..\..\dist"
#endif

[Setup]
AppId={{6F1D2A4E-8C3B-4F7A-9E21-5B0C7D3E9A14}
AppName=Nocturne
AppVersion={#AppVersion}
AppVerName=Nocturne {#AppVersion}
AppPublisher=Nocturne
AppPublisherURL=https://github.com/Lenny-Akumu/Nocturne
AppSupportURL=https://github.com/Lenny-Akumu/Nocturne/issues
AppUpdatesURL=https://github.com/Lenny-Akumu/Nocturne/releases
DefaultDirName={localappdata}\Programs\Nocturne
DefaultGroupName=Nocturne
DisableProgramGroupPage=yes
DisableReadyPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#OutDir}
OutputBaseFilename=Nocturne-Setup-{#AppVersion}
SetupIconFile=..\..\composeApp\icons\nocturne.ico
UninstallDisplayIcon={app}\Nocturne.exe
UninstallDisplayName=Nocturne
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "ukrainian"; MessagesFile: "compiler:Languages\Ukrainian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Nocturne"; Filename: "{app}\Nocturne.exe"
Name: "{group}\{cm:UninstallProgram,Nocturne}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\Nocturne"; Filename: "{app}\Nocturne.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Nocturne.exe"; Description: "{cm:LaunchProgram,Nocturne}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\Nocturne.exe"; Flags: nowait; Check: WizardSilent

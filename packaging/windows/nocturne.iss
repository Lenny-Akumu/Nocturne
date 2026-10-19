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
UsePreviousAppDir=no
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

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Run]
Filename: "{app}\Nocturne.exe"; Description: "{cm:LaunchProgram,Nocturne}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\Nocturne.exe"; Flags: nowait; Check: WizardSilent

[Code]
const
  UninstallRoot = 'Software\Microsoft\Windows\CurrentVersion\Uninstall';
  OwnKey = '{6F1D2A4E-8C3B-4F7A-9E21-5B0C7D3E9A14}_is1';

procedure KillApp();
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM Nocturne.exe', '', SW_HIDE, ewWaitUntilTerminated, Code);
  Sleep(500);
end;

procedure RemoveOtherCopies(RootKey: Integer; IsCurrentUser: Boolean);
var
  Names: TArrayOfString;
  I, Code: Integer;
  Key, DisplayName, Uninstaller: String;
  WindowsInstaller: Cardinal;
begin
  if not RegGetSubkeyNames(RootKey, UninstallRoot, Names) then
    Exit;
  for I := 0 to GetArrayLength(Names) - 1 do
  begin
    Key := UninstallRoot + '\' + Names[I];
    if RegQueryStringValue(RootKey, Key, 'DisplayName', DisplayName) and (Pos('Nocturne', DisplayName) = 1) then
    begin
      if RegQueryDWordValue(RootKey, Key, 'WindowsInstaller', WindowsInstaller) and (WindowsInstaller = 1) then
      begin
        Log('Removing legacy MSI install ' + Names[I]);
        ShellExec('', 'msiexec.exe', '/x ' + Names[I] + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Code);
      end
      else if (CompareText(Names[I], OwnKey) = 0) and not IsCurrentUser then
      begin
        if RegQueryStringValue(RootKey, Key, 'UninstallString', Uninstaller) then
        begin
          Log('Removing all-users install');
          ShellExec('', RemoveQuotes(Uninstaller), '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_HIDE, ewWaitUntilTerminated, Code);
        end;
      end;
    end;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  KillApp();
  RemoveOtherCopies(HKEY_CURRENT_USER, True);
  RemoveOtherCopies(HKEY_LOCAL_MACHINE, False);
  Result := '';
end;

function InitializeUninstall(): Boolean;
begin
  KillApp();
  Result := True;
end;

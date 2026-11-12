#ifndef AppVersion
  #define AppVersion "0"
#endif
#ifndef InstallerVersion
  #define InstallerVersion "0.0.0"
#endif
#ifndef SourceDir
  #error SourceDir is required
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif

[Setup]
AppId={{B6E0F7A2-4C1D-4E8B-9A3F-2D5C7E1B9F40}
AppName=SinusChat
AppVersion={#AppVersion}
AppVerName=SinusChat-Desktop {#AppVersion}
AppPublisher=Sinusoide
AppPublisherURL=https://sinusoide.pl
VersionInfoVersion={#InstallerVersion}
VersionInfoDescription=SinusChat-Desktop Setup
VersionInfoProductName=SinusChat-Desktop
CreateAppDir=no
Uninstallable=no
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
OutputBaseFilename=SinusChat-Setup-{#AppVersion}
SetupIconFile=..\icons\sinuschat.ico
Compression=lzma2/max
SolidCompression=yes
DisableStartupPrompt=yes

[Files]
Source: "splash-100.bmp"; Flags: dontcopy
Source: "splash-125.bmp"; Flags: dontcopy
Source: "splash-150.bmp"; Flags: dontcopy
Source: "splash-175.bmp"; Flags: dontcopy
Source: "splash-200.bmp"; Flags: dontcopy
Source: "{#SourceDir}\*"; DestDir: "{tmp}\app"; Flags: dontcopy recursesubdirs createallsubdirs

[Code]
const
  GWL_EXSTYLE = -20;
  WS_EX_LAYERED = $80000;
  WS_EX_TOOLWINDOW = $80;
  LWA_COLORKEY = 1;

var
  Splash: TSetupForm;

function GetWindowLong(Wnd: HWND; Index: Integer): Longint; external 'GetWindowLongW@user32.dll stdcall';
function SetWindowLong(Wnd: HWND; Index: Integer; NewLong: Longint): Longint; external 'SetWindowLongW@user32.dll stdcall';
function SetLayeredWindowAttributes(Wnd: HWND; Key: DWORD; Alpha: Byte; Flags: DWORD): BOOL; external 'SetLayeredWindowAttributes@user32.dll stdcall';

procedure ShowSplash;
var
  Img: TBitmapImage;
  Size: Integer;
  Name: String;
begin
  Size := ScaleX(160);
  if Size >= 300 then
    Name := 'splash-200.bmp'
  else if Size >= 260 then
    Name := 'splash-175.bmp'
  else if Size >= 220 then
    Name := 'splash-150.bmp'
  else if Size >= 185 then
    Name := 'splash-125.bmp'
  else
    Name := 'splash-100.bmp';
  ExtractTemporaryFile(Name);
  Splash := CreateCustomForm(Size, Size, False, False);
  Splash.BorderStyle := bsNone;
  Splash.Position := poScreenCenter;
  Splash.Color := $FF00FF;
  Img := TBitmapImage.Create(Splash);
  Img.Parent := Splash;
  Img.Bitmap.LoadFromFile(ExpandConstant('{tmp}\' + Name));
  Img.AutoSize := True;
  Splash.ClientWidth := Img.Bitmap.Width;
  Splash.ClientHeight := Img.Bitmap.Height;
  SetWindowLong(Splash.Handle, GWL_EXSTYLE, GetWindowLong(Splash.Handle, GWL_EXSTYLE) or WS_EX_LAYERED or WS_EX_TOOLWINDOW);
  SetLayeredWindowAttributes(Splash.Handle, $FF00FF, 255, LWA_COLORKEY);
  Splash.Show;
  Splash.Refresh;
end;

function InitializeSetup(): Boolean;
var
  Code: Integer;
  Params: String;
begin
  Result := False;
  try
    ShowSplash;
  except
  end;
  ExtractTemporaryFiles('{tmp}\app\*');
  if Splash <> nil then
    Splash.Hide;
  Params := '--setup';
  if Pos('--update', GetCmdTail) > 0 then
    Params := Params + ' --update';
  if not Exec(ExpandConstant('{tmp}\app\SinusChat.exe'), Params, ExpandConstant('{tmp}\app'), SW_SHOWNORMAL, ewWaitUntilTerminated, Code) then
    MsgBox(SysErrorMessage(Code), mbError, MB_OK);
end;

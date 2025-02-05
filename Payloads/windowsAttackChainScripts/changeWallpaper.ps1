param(
    [Parameter(Mandatory=$true)]
    [string]$wallpaperURL
)

$fileExtension = $wallpaperURL.Substring($wallpaperURL.LastIndexOf('.') + 1)

$wallpaper = "C:\\Windows\\fonts\\wallpaper.$($fileExtension)"

# Download wallpaper
Invoke-WebRequest -OutFile $wallpaper $wallpaperURL

# Add-Type to include the user32.dll library
$code = @"
using System.Runtime.InteropServices;
namespace Win32 {
    public class Wallpaper {
        [DllImport("user32.dll", CharSet = CharSet.Auto)]
        public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);
        public static void SetWallpaper(string thePath) {
            SystemParametersInfo(20, 0, thePath, 3);
        }
    }
}
"@
Add-Type -TypeDefinition $code

# Set the wallpaper
[Win32.Wallpaper]::SetWallpaper($wallpaper)
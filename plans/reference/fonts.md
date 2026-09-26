# Fonts to bundle (verified 2026-09-25: HTTP 200, sizes match; licence md5 24cd6c256d592d23fdc3ef640e05f0ed)

Source repo: https://github.com/iaolo/iA-Fonts (SIL OFL 1.1, Reserved Font Name "iA Writer", based on IBM Plex).
Ship the files **unmodified** (renaming the *file* is fine; the internal name table stays untouched).

| res/font name | URL | Bytes |
|---|---|---|
| duo_regular.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Duo/Static/iAWriterDuoS-Regular.ttf | 120696 |
| duo_bold.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Duo/Static/iAWriterDuoS-Bold.ttf | 121460 |
| duo_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Duo/Static/iAWriterDuoS-Italic.ttf | 105824 |
| duo_bold_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Duo/Static/iAWriterDuoS-BoldItalic.ttf | 105252 |
| quattro_regular.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Quattro/Static/iAWriterQuattroS-Regular.ttf | 119772 |
| quattro_bold.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Quattro/Static/iAWriterQuattroS-Bold.ttf | 120404 |
| quattro_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Quattro/Static/iAWriterQuattroS-Italic.ttf | 105028 |
| quattro_bold_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Quattro/Static/iAWriterQuattroS-BoldItalic.ttf | 104520 |
| mono_regular.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Mono/Static/iAWriterMonoS-Regular.ttf | 97044 |
| mono_bold.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Mono/Static/iAWriterMonoS-Bold.ttf | 96168 |
| mono_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Mono/Static/iAWriterMonoS-Italic.ttf | 104120 |
| mono_bold_italic.ttf | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Mono/Static/iAWriterMonoS-BoldItalic.ttf | 103400 |
| licence → `app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt` | https://raw.githubusercontent.com/iaolo/iA-Fonts/master/iA%20Writer%20Duo/LICENSE.md | 4506 |

Font facts: UPM 1000; hhea 1025/−275/0 → natural line box 1.30 em; Mono advance 0.60 em for every glyph; Duo 0.60 em
except `m M w W` = 0.90 em. `iAWriterQuattroS-Bold/-BoldItalic` declare usWeightClass 400 → always declare weights in
the font-family XML. Do not bundle the variable (`V`) fonts in v1.

Family XML (same pattern for quattro.xml / mono.xml):
```xml
<font-family xmlns:android="http://schemas.android.com/apk/res/android">
    <font android:font="@font/duo_regular"     android:fontStyle="normal" android:fontWeight="400"/>
    <font android:font="@font/duo_italic"      android:fontStyle="italic" android:fontWeight="400"/>
    <font android:font="@font/duo_bold"        android:fontStyle="normal" android:fontWeight="700"/>
    <font android:font="@font/duo_bold_italic" android:fontStyle="italic" android:fontWeight="700"/>
</font-family>
```

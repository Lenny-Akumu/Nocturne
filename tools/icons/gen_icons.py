#!/usr/bin/env python3
"""Generates every Nocturne app icon asset from one geometry (viewBox 0 0 200 200).

Rerun after changing palettes/backgrounds: python3 tools/icons/gen_icons.py
Needs Chromium (CHROMIUM env, default /opt/pw-browsers/chromium) and Pillow.
Keep PALETTES/BACKGROUNDS in sync with ui/NocturneIcon.kt and data/Settings.kt.
"""
import io, json, os, re, shutil, subprocess, sys, tempfile
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
CHROMIUM = os.environ.get('CHROMIUM', '/opt/pw-browsers/chromium')
RES = f'{ROOT}/composeApp/src/androidMain/res'
XCASSETS = f'{ROOT}/iosApp/iosApp/Assets.xcassets'

# name: (arcLight, arcDeep, wave0, wave1, wave2)
PALETTES = {
    'standard': ('#D9C8FF', '#7B4DF0', '#E2D4FF', '#9B6BFF', '#4B2A9E'),
    'system':   ('#D9C8FF', '#7B4DF0', '#E2D4FF', '#9B6BFF', '#4B2A9E'),  # Android 12+ uses system_accent1_*
    'ocean':    ('#C9E8FF', '#2F7BEB', '#D6F0FF', '#3DA5FF', '#1B3F9E'),
    'sunset':   ('#FFD3B8', '#E8506A', '#FFE3C7', '#FF7A59', '#A82C6B'),
    'emerald':  ('#C6F3DD', '#14A37F', '#D4FBE8', '#34D399', '#0E6B5A'),
    'rose':     ('#FFCCDB', '#E0386A', '#FFDAE5', '#FF5C8A', '#9A1E46'),
    'gold':     ('#FFE7A8', '#E89B1C', '#FFF2C9', '#FFBC3D', '#A5600C'),
    'mono':     ('#F0F1F4', '#8C9099', '#FFFFFF', '#BFC3CC', '#5A5E68'),
    'aurora':   ('#C6FFF0', '#8A5CF5', '#D2FFF5', '#4FD1C5', '#7B4DF0'),
}
BACKGROUNDS = ['none', 'acrylic', 'circle', 'square']
DEFAULT = ('square', 'standard')
SYSTEM_V31 = ('@android:color/system_accent1_200', '@android:color/system_accent1_500',
              '@android:color/system_accent1_100', '@android:color/system_accent1_400', '@android:color/system_accent1_600')

ARC_LIGHT = 'M100 32 A68 68 0 0 1 168 100'
ARC_DEEP = 'M100 168 A68 68 0 0 1 32 100'
WAVE = 'M56 100 Q70 72 84 100 T112 100 T144 100'
# objectBoundingBox gradient (0,0)->(1,1) over the wave bbox (56,86)-(144,114), expressed in user space.
GRAD = (56, 86, 72.18, 136.85)


def cap(s): return s[0].upper() + s[1:]


def alias(bg, pal): return f'Icon{cap(bg)}{cap(pal)}'


def svg(bg, pal, ios=False, size=1024):
    al, ad, w0, w1, w2 = PALETTES[pal]
    defs = (f'<linearGradient id="g" gradientUnits="userSpaceOnUse" x1="{GRAD[0]}" y1="{GRAD[1]}" x2="{GRAD[2]}" y2="{GRAD[3]}">'
            f'<stop offset="0" stop-color="{w0}"/><stop offset=".5" stop-color="{w1}"/><stop offset="1" stop-color="{w2}"/></linearGradient>')
    back = ''
    if bg == 'square' or (ios and bg == 'none'):
        back = '<rect width="200" height="200" fill="#000"/>' if ios else '<rect width="200" height="200" rx="44" fill="#000"/>'
    elif bg == 'circle':
        back = ('<rect width="200" height="200" fill="url(#ab)"/>' if ios else '') + '<circle cx="100" cy="100" r="100" fill="#000"/>'
    elif bg == 'acrylic':
        shape = '<rect width="200" height="200"' if ios else '<circle cx="100" cy="100" r="100"'
        back = (f'{shape} fill="url(#ab)"/>{shape} fill="url(#at)"/>{shape} filter="url(#n)" fill="#fff"/>'
                + ('' if ios else '<circle cx="100" cy="100" r="99" fill="none" stroke="url(#rim)" stroke-width="2"/>'))
    if bg in ('acrylic', 'circle'):
        op = ('1', '1') if ios else ('.55', '.72')
        defs += (f'<linearGradient id="ab" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#3A3450" stop-opacity="{op[0]}"/>'
                 f'<stop offset="1" stop-color="#121018" stop-opacity="{op[1]}"/></linearGradient>'
                 f'<radialGradient id="at" gradientUnits="userSpaceOnUse" cx="80" cy="70" r="120"><stop offset="0" stop-color="{w1}" stop-opacity=".24"/>'
                 f'<stop offset="1" stop-color="{w1}" stop-opacity="0"/></radialGradient>'
                 '<linearGradient id="rim" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".38"/>'
                 '<stop offset=".5" stop-color="#fff" stop-opacity=".05"/><stop offset="1" stop-color="#fff" stop-opacity=".14"/></linearGradient>'
                 '<filter id="n" x="0" y="0" width="1" height="1"><feTurbulence type="fractalNoise" baseFrequency="1.4" numOctaves="2" seed="7"/>'
                 '<feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  .22 0 0 0 -.06"/><feComposite in2="SourceGraphic" operator="in"/></filter>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 200 200"><defs>{defs}</defs>{back}'
            f'<g fill="none" stroke-linecap="round"><path d="{ARC_LIGHT}" stroke="{al}" stroke-width="12"/>'
            f'<path d="{ARC_DEEP}" stroke="{ad}" stroke-width="12"/><path d="{WAVE}" stroke="url(#g)" stroke-width="14"/></g></svg>')


def render(svg_text, size=1024):
    svg_text = re.sub(r'width="\d+" height="\d+"', f'width="{size}" height="{size}"', svg_text, count=1)
    with tempfile.TemporaryDirectory() as d:
        html, out = f'{d}/i.html', f'{d}/o.png'
        open(html, 'w').write(f'<html><body style="margin:0;background:transparent">{svg_text}</body></html>')
        subprocess.run([CHROMIUM, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                        '--default-background-color=00000000', f'--window-size={size},{size + 400}', f'--screenshot={out}', f'file://{html}'],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return Image.open(out).convert('RGBA').crop((0, 0, size, size))


def save_png(img, path, rgb=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    (img.convert('RGB') if rgb else img).save(path, optimize=True)


def default_assets():
    big = render(svg(*DEFAULT))
    exact = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect width="200" height="200" rx="44" fill="#000"/>\n'
             '<g fill="none" stroke-linecap="round">\n'
             f'<path d="{ARC_LIGHT}" stroke="#D9C8FF" stroke-width="12"/>\n<path d="{ARC_DEEP}" stroke="#7B4DF0" stroke-width="12"/>\n'
             f'<path d="{WAVE}" stroke="url(#g)" stroke-width="14"/></g>\n'
             '<linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E2D4FF"/><stop offset=".5" stop-color="#9B6BFF"/>'
             '<stop offset="1" stop-color="#4B2A9E"/></linearGradient>\n</svg>\n')
    open(f'{ROOT}/docs/icon.svg', 'w').write(exact)
    save_png(big, f'{ROOT}/docs/icon-1024.png')
    save_png(big.resize((512, 512), Image.LANCZOS), f'{ROOT}/docs/icon.png')
    bare = svg('none', 'standard')
    open(f'{ROOT}/composeApp/icons/nocturne-transparent.svg', 'w').write(bare + '\n')
    save_png(render(bare), f'{ROOT}/composeApp/icons/nocturne-transparent-1024.png')
    sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    imgs = {n: big.resize((n, n), Image.LANCZOS) for n in sizes}
    for n, im in imgs.items():
        save_png(im, f'{ROOT}/composeApp/src/desktopMain/resources/icons/nocturne-{n}.png')
    save_png(imgs[256], f'{ROOT}/composeApp/src/desktopMain/resources/nocturne.png')
    imgs[256].save(f'{ROOT}/composeApp/icons/nocturne.ico', sizes=[(n, n) for n in sizes])


def ios_assets():
    for d in os.listdir(XCASSETS):
        if d.startswith('AppIcon-') and d.endswith('.appiconset'):
            shutil.rmtree(f'{XCASSETS}/{d}')
    n = 0
    for pal in PALETTES:
        if pal == 'system':
            continue
        for bg in BACKGROUNDS:
            if bg == 'none':  # iOS icons are opaque: "None" falls back to the square
                continue
            name = 'AppIcon' if (bg, pal) == DEFAULT else f'AppIcon-{cap(bg)}{cap(pal)}'
            d = f'{XCASSETS}/{name}.appiconset'
            save_png(render(svg(bg, pal, ios=True)), f'{d}/icon.png', rgb=True)
            json.dump({'images': [{'filename': 'icon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}],
                       'info': {'author': 'xcode', 'version': 1}}, open(f'{d}/Contents.json', 'w'), indent=2)
            n += 1
    return n


# ---- Android vector drawables (108dp adaptive canvas; the 200-unit design fills the 72dp visible area) ----
VEC_HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/icons/gen_icons.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
GROUP = '    <group android:scaleX="0.36" android:scaleY="0.36" android:translateX="18" android:translateY="18">\n'


def a(c):  # #RRGGBB -> #FFRRGGBB, resource refs untouched
    return c if c.startswith('@') else '#FF' + c[1:].upper()


def fg_vector(cols):
    al, ad, w0, w1, w2 = cols
    return (VEC_HEAD + GROUP +
            f'        <path android:pathData="{ARC_LIGHT}" android:strokeColor="{a(al)}" android:strokeWidth="12" android:strokeLineCap="round" />\n'
            f'        <path android:pathData="{ARC_DEEP}" android:strokeColor="{a(ad)}" android:strokeWidth="12" android:strokeLineCap="round" />\n'
            f'        <path android:pathData="{WAVE}" android:strokeWidth="14" android:strokeLineCap="round">\n'
            '            <aapt:attr name="android:strokeColor">\n'
            f'                <gradient android:type="linear" android:startX="{GRAD[0]}" android:startY="{GRAD[1]}" android:endX="{GRAD[2]}" android:endY="{GRAD[3]}">\n'
            f'                    <item android:offset="0" android:color="{a(w0)}" />\n'
            f'                    <item android:offset="0.5" android:color="{a(w1)}" />\n'
            f'                    <item android:offset="1" android:color="{a(w2)}" />\n'
            '                </gradient>\n            </aapt:attr>\n        </path>\n    </group>\n</vector>\n')


def mono_vector():
    return (VEC_HEAD + GROUP + ''.join(
        f'        <path android:pathData="{p}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{w}" android:strokeLineCap="round" />\n'
        for p, w in ((ARC_LIGHT, 12), (ARC_DEEP, 12), (WAVE, 14))) + '    </group>\n</vector>\n')


def bg_vector(bg):
    body = {
        'none': '    <path android:fillColor="#00000000" android:pathData="M0,0h108v108h-108z" />\n',
        'square': '    <path android:fillColor="#FF000000" android:pathData="M0,0h108v108h-108z" />\n',
        'circle': '    <path android:fillColor="#FF000000" android:pathData="M18,54a36,36 0,1 0,72 0a36,36 0,1 0,-72 0z" />\n',
        # Launchers mask and composite over the wallpaper: a translucent frosted tint with a soft highlight.
        'acrylic': ('    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
                    '            <gradient android:type="linear" android:startX="18" android:startY="18" android:endX="90" android:endY="90">\n'
                    '                <item android:offset="0" android:color="#8C3A3450" />\n'
                    '                <item android:offset="1" android:color="#B8121018" />\n'
                    '            </gradient>\n        </aapt:attr>\n    </path>\n'
                    '    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
                    '            <gradient android:type="radial" android:centerX="42" android:centerY="36" android:gradientRadius="60">\n'
                    '                <item android:offset="0" android:color="#2EFFFFFF" />\n'
                    '                <item android:offset="1" android:color="#00FFFFFF" />\n'
                    '            </gradient>\n        </aapt:attr>\n    </path>\n'),
    }[bg]
    return VEC_HEAD + body + '</vector>\n'


def fg_name(pal): return 'ic_launcher_foreground' if pal == 'standard' else f'ic_launcher_fg_{pal}'
def bg_name(bg): return 'ic_launcher_background' if bg == 'square' else f'ic_launcher_bg_{bg}'
def mip_name(bg, pal): return 'ic_launcher' if (bg, pal) == DEFAULT else f'ic_launcher_{bg}_{pal}'


def android_assets():
    for d in ('drawable', 'drawable-v31', 'mipmap-anydpi-v26'):
        os.makedirs(f'{RES}/{d}', exist_ok=True)
        for f in os.listdir(f'{RES}/{d}'):
            if re.match(r'ic_launcher_(fg|bg)_|ic_launcher_(none|acrylic|circle|square)_', f):
                os.remove(f'{RES}/{d}/{f}')
    w = lambda p, s: open(f'{RES}/{p}', 'w').write(s)
    for pal, cols in PALETTES.items():
        w(f'drawable/{fg_name(pal)}.xml', fg_vector(cols))
    w(f'drawable-v31/{fg_name("system")}.xml', fg_vector(SYSTEM_V31))
    for bg in BACKGROUNDS:
        w(f'drawable/{bg_name(bg)}.xml', bg_vector(bg))
    w('drawable/ic_launcher_monochrome.xml', mono_vector())
    aliases = []
    for bg in BACKGROUNDS:
        for pal in PALETTES:
            m = mip_name(bg, pal)
            w(f'mipmap-anydpi-v26/{m}.xml',
              '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
              f'    <background android:drawable="@drawable/{bg_name(bg)}" />\n    <foreground android:drawable="@drawable/{fg_name(pal)}" />\n'
              '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
            on = 'true' if (bg, pal) == DEFAULT else 'false'
            aliases.append(f'        <activity-alias android:name=".{alias(bg, pal)}" android:targetActivity=".MainActivity" android:exported="true" '
                           f'android:enabled="{on}" android:icon="@mipmap/{m}" android:roundIcon="@mipmap/{m}">\n'
                           '            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>\n'
                           '        </activity-alias>\n')
    aliases.append('        <activity-alias android:name=".IconHalloween" android:targetActivity=".MainActivity" android:exported="true" '
                   'android:enabled="false" android:icon="@mipmap/ic_launcher_halloween" android:roundIcon="@mipmap/ic_launcher_halloween">\n'
                   '            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>\n'
                   '        </activity-alias>\n')
    mf = f'{ROOT}/composeApp/src/androidMain/AndroidManifest.xml'
    text = open(mf).read()
    start, end = '        <!-- App icon aliases: generated by tools/icons/gen_icons.py -->\n', '        <!-- /App icon aliases -->\n'
    block = start + ''.join(aliases) + end
    if start in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        i = text.index('    </activity>\n') + len('    </activity>\n')
        text = text[:i] + block + text[i:]
    open(mf, 'w').write(text)
    return len(aliases)


# ---- Halloween (28 Oct – 1 Nov): pumpkin and bat over an orange wave; keep in sync with ui/NocturneIcon.kt ----
HALLOWEEN_ART = (
    '<defs><linearGradient id="hg" gradientUnits="userSpaceOnUse" x1="56" y1="86" x2="72.18" y2="136.85">'
    '<stop offset="0" stop-color="#FFE3A3"/><stop offset=".5" stop-color="#FF8A1F"/><stop offset="1" stop-color="#B8380A"/></linearGradient>'
    '<radialGradient id="hp" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#FFB347"/><stop offset="1" stop-color="#D45A08"/></radialGradient></defs>'
    f'<g fill="none" stroke-linecap="round"><path d="{ARC_LIGHT}" stroke="#FFC774" stroke-width="12"/><path d="{ARC_DEEP}" stroke="#F06A0E" stroke-width="12"/>'
    f'<path d="{WAVE}" stroke="url(#hg)" stroke-width="14"/></g>'
    '<g transform="translate(46 50)"><path d="M0 -14 C -1 -19 2 -23 6 -24" stroke="#5A8A2E" stroke-width="3.5" stroke-linecap="round" fill="none"/>'
    '<ellipse cx="-6" cy="0" rx="10" ry="13" fill="url(#hp)"/><ellipse cx="6" cy="0" rx="10" ry="13" fill="url(#hp)"/><ellipse cx="0" cy="0" rx="11" ry="14" fill="url(#hp)"/>'
    '<path d="M-7 -3 l4 0 l-2 -4z M3 -3 l4 0 l-2 -4z" fill="#2A1004"/><path d="M-7 4 q7 5 14 0" stroke="#2A1004" stroke-width="1.8" stroke-linecap="round" fill="none"/></g>'
    '<g transform="translate(150 150) scale(1.15)" fill="#F06A0E">'
    '<path d="M0 -3 C -2 -7 -3 -9 -3.5 -11 L -2 -8.5 L 0 -9.5 L 2 -8.5 L 3.5 -11 C 3 -9 2 -7 0 -3 Z"/>'
    '<path d="M-2.5 -4 C -8 -9 -16 -10 -24 -6 C -20 -5 -19 -2 -19.5 1 C -17 -1.5 -14 -1.5 -12.5 1.5 C -11 -1 -8 -1 -6.5 2 C -5 -1 -3 -1 -2 0 Z"/>'
    '<path d="M2.5 -4 C 8 -9 16 -10 24 -6 C 20 -5 19 -2 19.5 1 C 17 -1.5 14 -1.5 12.5 1.5 C 11 -1 8 -1 6.5 2 C 5 -1 3 -1 2 0 Z"/>'
    '<ellipse cx="0" cy="-2.5" rx="3.6" ry="5.2"/></g>'
)
HALLOWEEN_BG = '<radialGradient id="hb" cx="50%" cy="45%" r="70%"><stop offset="0" stop-color="#2A1206"/><stop offset="1" stop-color="#0A0604"/></radialGradient>'


def halloween_svg(view='0 0 200 200', back=True, size=1024):
    bg = f'<defs>{HALLOWEEN_BG}</defs><rect x="-50" y="-50" width="300" height="300" fill="url(#hb)"/>' if back else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{bg}{HALLOWEEN_ART}</svg>'


def halloween_assets():
    # iOS: opaque full-bleed square
    d = f'{XCASSETS}/AppIcon-Halloween.appiconset'
    save_png(render(halloween_svg()), f'{d}/icon.png', rgb=True)
    json.dump({'images': [{'filename': 'icon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}],
               'info': {'author': 'xcode', 'version': 1}}, open(f'{d}/Contents.json', 'w'), indent=2)
    # Android adaptive icon: the 200-unit art fills the 72dp safe zone of the 108dp canvas
    save_png(render(halloween_svg('-50 -50 300 300', back=False, size=432), 432), f'{RES}/drawable-nodpi/ic_launcher_fg_halloween.png')
    bg_only = f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="-50 -50 300 300"><defs>{HALLOWEEN_BG}</defs><rect x="-50" y="-50" width="300" height="300" fill="url(#hb)"/></svg>'
    save_png(render(bg_only, 432), f'{RES}/drawable-nodpi/ic_launcher_bg_halloween.png')
    open(f'{RES}/mipmap-anydpi-v26/ic_launcher_halloween.xml', 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_bg_halloween" />\n    <foreground android:drawable="@drawable/ic_launcher_fg_halloween" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')


if __name__ == '__main__':
    only = sys.argv[1:] or ['default', 'ios', 'android']
    if 'default' in only: default_assets()
    if 'ios' in only: print('iOS icon sets:', ios_assets())
    if 'ios' in only or 'halloween' in only: halloween_assets()
    if 'android' in only: print('Android aliases:', android_assets())

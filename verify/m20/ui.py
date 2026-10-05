import subprocess,re,time,sys
D="emulator-5572"
def sh(c): return subprocess.run(c,shell=True,capture_output=True,text=True).stdout
def dump(): return sh(f"adb -s {D} exec-out uiautomator dump /dev/tty")
def texts(x=None): return re.findall(r'(?:text|content-desc)="([^"]{1,90})"', x or dump())
def find(label, x=None, attr="(?:text|content-desc)"):
    x = x or dump()
    m = re.search(attr+r'="'+re.escape(label)+r'"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
    if not m:
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?'+attr+r'="'+re.escape(label)+'"', x)
        if m: pass
    return tuple(map(int,m.groups())) if m else None
def tap(label, wait=1.5):
    b = find(label)
    if not b: print("  !! not found:", label); return False
    a,b2,c,d = b; sh(f"adb -s {D} shell input tap {(a+c)//2} {(b2+d)//2}"); time.sleep(wait); return True
def shot(name): sh(f"adb -s {D} exec-out screencap -p > /tmp/polish/m20/{name}.png")

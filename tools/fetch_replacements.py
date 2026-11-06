import sys,os,socket
_orig=socket.getaddrinfo
socket.getaddrinfo=lambda h,p,family=0,type=0,proto=0,flags=0:_orig(h,p,socket.AF_INET,type,proto,flags)
sys.path.insert(0,r"D:\zhenghebaoxiangmu\mogaidiaolin\tools")
import pack_plan as P, hashlib
import urllib.request
dest_dir=r"D:\zhenghebaoxiangmu\mogaidiaolin\_build\dl\mods"
os.makedirs(dest_dir,exist_ok=True)
for old,info in P.REPLACE.items():
    dest=os.path.join(dest_dir,info["filename"])
    if os.path.exists(dest) and hashlib.sha1(open(dest,'rb').read()).hexdigest()==info["sha1"]:
        print("ok cached",info["filename"]); continue
    req=urllib.request.Request(info["url"],headers={"User-Agent":"wc-pack/1.0"})
    data=urllib.request.urlopen(req,timeout=120).read()
    assert hashlib.sha1(data).hexdigest()==info["sha1"], "sha1 mismatch "+info["filename"]
    open(dest,"wb").write(data)
    print("downloaded",info["filename"],len(data))

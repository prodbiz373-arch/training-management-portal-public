#!/usr/bin/env python3
"""Read-only Linux network guard. No DB, subprocess, environment or network-connect APIs."""
import json
import os
from pathlib import Path
import socket
import struct
import sys

class Rejected(Exception):
    def __init__(self, stage, code):
        self.stage, self.code = stage, code

def require(ok, stage, code):
    if not ok:
        raise Rejected(stage, code)

# Diagnostic baseline from LinuxKit 7.0.14. Names alone never authorize a device.
DEVICES = {
    'tunl0': ('ipip', 768), 'gre0': ('gre', 778),
    'gretap0': ('gretap', 1), 'erspan0': ('erspan', 1),
    'ip_vti0': ('vti', 768), 'ip6_vti0': ('vti6', 769),
    'sit0': ('sit', 776), 'ip6tnl0': ('ip6tnl', 769), 'ip6gre0': ('ip6gre', 823),
}

def zero_profile(widths, overrides=None):
    p = {k: bytes(n) for k,n in widths.items()}
    p.update(overrides or {})
    return p

ENCAP = {15:2,17:2,18:2,16:2}
IPIP = {1:4,2:4,3:4,4:1,5:1,9:1,10:1,20:4,**ENCAP}
GRE = {1:4,2:2,3:2,4:4,5:4,6:4,7:4,8:1,9:1,10:1,20:4,14:2,16:2,17:2,15:2,19:1}
PROFILES = {
 'ipip': zero_profile(IPIP),
 'gre': zero_profile(GRE), 'gretap': zero_profile(GRE),
 'erspan': zero_profile({22:1,21:4,**GRE},{22:b'\x01',3:b'\x20\x00'}),
 'vti': zero_profile({1:4,2:4,3:4,4:4,5:4,6:4}),
 'vti6': zero_profile({1:4,4:16,5:16,2:4,3:4,6:4}),
 'sit': zero_profile({**IPIP,8:2,11:16,12:4,13:2,14:2},
                     {4:b'\x40',9:b'\x29',11:bytes.fromhex('20020000000000000000000000000000'),13:b'\x10\x00'}),
 'ip6tnl': zero_profile({1:4,2:16,3:16,4:1,6:1,7:4,8:4,9:1,20:4,**ENCAP},
                         {8:b'\x00\x00\x04\x00',9:b'\x29'}),
 'ip6gre': zero_profile({1:4,2:2,3:2,4:4,5:4,6:16,7:16,8:1,11:1,12:4,13:4,20:4,14:2,16:2,17:2,15:2}),
}
# Explicit known Linux IFLA metadata IDs. Tunnel LINKINFO is independently strict.
# Informational fields include MTU, statistics, queues, offload limits and AF_SPEC.
LINK_ATTRS = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,
              21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,
              39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,
              56,57,58,59,60,61,62,63,64,65,66,67,68,69}

def attrs(data, allowed=None):
    result={}; offset=0
    while offset < len(data):
        require(len(data)-offset >= 4, 'netlink', 'attribute-header')
        length, raw=struct.unpack_from('=HH',data,offset)
        kind=raw & 0x3fff
        require(length>=4 and offset+length<=len(data), 'netlink','attribute-length')
        require(kind not in result, 'netlink','duplicate-attribute')
        require(allowed is None or kind in allowed, 'netlink','unknown-attribute')
        result[kind]=data[offset+4:offset+length]
        offset+=(length+3)&~3
    require(offset==len(data), 'netlink','attribute-padding')
    return result

def dump(kind,payload):
    with socket.socket(socket.AF_NETLINK,socket.SOCK_RAW,socket.NETLINK_ROUTE) as s:
        s.settimeout(5);s.bind((0,0));seq=93
        s.send(struct.pack('=IHHII',16+len(payload),kind,0x301,seq,0)+payload)
        rows=[]
        while True:
            data,ancillary,msgflags,peer=s.recvmsg(1048576)
            require(peer[0]==0 and not msgflags & socket.MSG_TRUNC,'netlink','sender-or-truncation')
            offset=0
            while offset<len(data):
                require(len(data)-offset>=16,'netlink','message-header')
                length,typ,flags,sequence,pid=struct.unpack_from('=IHHII',data,offset)
                require(length>=16 and offset+length<=len(data) and sequence==seq,'netlink','message-length-sequence')
                require(not flags & 0x10,'netlink','interrupted-dump')
                body=data[offset+16:offset+length]
                if typ==3:
                    require(len(body)>=4 and struct.unpack_from('=i',body)[0]==0,'netlink','dump-status')
                    return rows
                require(typ==kind-2,'netlink','unexpected-message')
                rows.append(body);offset+=(length+3)&~3

def uint(value):
    require(len(value)==4,'netlink','integer-size')
    return struct.unpack('=I',value)[0]

def text(value):
    require(value.endswith(b'\0') and b'\0' not in value[:-1],'netlink','string-format')
    return value[:-1].decode('ascii')

def collect():
    links=[]
    for b in dump(18,struct.pack('=BBHiII',0,0,0,0,0,0)):
        require(len(b)>=16,'netlink','link-header')
        family,pad,hw,idx,flags,change=struct.unpack_from('=BBHiII',b)
        a=attrs(b[16:],LINK_ATTRS)
        # Linux UAPI: NETNS_IMMUTABLE(u8), HEADROOM(u16), TAILROOM(u16).
        for attr,size in ((67,1),(68,2),(69,2)):
            require(attr not in a or len(a[attr])==size,'netlink','metadata-size')
        require(67 not in a or a[67] in (b'\0',b'\1'),'netlink','immutable-value')
        info=attrs(a[18],{1,2}) if 18 in a else {}
        require(3 in a and 16 in a,'netlink','missing-link-fields')
        # Master, alternate lower link or external namespace linkage is forbidden.
        require(all(k not in a or uint(a[k])==0 for k in (5,10)), 'links','linked-device')
        require(37 not in a,'links','external-netns')
        links.append({'name':text(a[3]),'index':idx,'type':hw,'flags':flags,
                      'operstate':a[16][0] if len(a[16])==1 else -1,
                      'kind':text(info[1]) if 1 in info else '',
                      'tunnel':attrs(info[2]) if 2 in info else {}})
    addresses=[]
    for b in dump(22,struct.pack('=BBBBI',0,0,0,0,0)):
        require(len(b)>=8,'netlink','address-header')
        f,prefix,flags,scope,idx=struct.unpack_from('=BBBBI',b)
        a=attrs(b[8:],{1,2,3,4,5,6,7,8,9,10,11})
        require(f in (2,10) and 1 in a,'addresses','family-or-address')
        require(not any(k in a for k in (4,5,7,9,10)),'addresses','unexpected-address-setting')
        require(11 not in a or a[11]==b'\x01','addresses','non-loopback-protocol')
        addresses.append((idx,f,prefix,scope,socket.inet_ntop(f,a[1]),socket.inet_ntop(f,a.get(2,a[1]))))
    routes=[]
    for b in dump(26,struct.pack('=BBBBBBBBI',0,0,0,0,0,0,0,0,0)):
        require(len(b)>=12,'netlink','route-header')
        f,prefix,src,tos,table,protocol,scope,rt,flags=struct.unpack_from('=BBBBBBBBI',b)
        a=attrs(b[12:],{1,4,6,7,12,15,20}) # gateway, multipath, nexthop etc. fail closed
        require(f in (2,10) and src==tos==flags==0,'routes','route-header-values')
        require(1 in a and 4 in a,'routes','missing-route-fields')
        if 6 in a: require(uint(a[6])==0,'routes','route-priority')
        if 12 in a: require(len(a[12])==32 and not any(a[12]),'routes','route-cache')
        if 20 in a: require(a[20]==b'\0','routes','route-preference')
        routes.append((f,prefix,uint(a[15]) if 15 in a else table,rt,scope,uint(a[4]),
                       socket.inet_ntop(f,a[1]),socket.inet_ntop(f,a[7]) if 7 in a else None))
    base=Path('/sys/class/net');syslinks={};regular=[]
    for p in base.iterdir():
        if p.is_symlink():
            syslinks[p.name]={k:(p/k).read_text().strip() for k in ('ifindex','type','flags','operstate','iflink')}
        else:
            require(p.is_file() and p.name=='bonding_masters','sysfs','unknown-entry');regular.append(p.name)
    proc=Path('/proc/self/net/dev').read_text().splitlines()
    require(len(proc)>=2 and 'Inter-' in proc[0] and 'face' in proc[1],'proc','dev-format')
    proc_names=[line.split(':',1)[0].strip() for line in proc[2:] if line.strip()]
    return {'links':links,'addresses':addresses,'routes':routes,'sys':syslinks,'regular':regular,
            'socket':socket.if_nameindex(),'proc':proc_names,
            'forward4':Path('/proc/sys/net/ipv4/conf/all/forwarding').read_text().strip(),
            'forward6':Path('/proc/sys/net/ipv6/conf/all/forwarding').read_text().strip(),
            'namespace':os.readlink('/proc/self/ns/net')}

def validate(s):
    links=s['links']; names=[x['name'] for x in links]
    require(len(names)==len(set(names)) and 'lo' in names,'consistency','duplicate-or-no-loopback')
    byname={x['name']:x for x in links}
    require(len({x['index'] for x in links})==len(links),'consistency','duplicate-index')
    require(set(names)==set(s['sys'])==set(s['proc'])=={n for i,n in s['socket']},'consistency','device-set')
    require(len(s['proc'])==len(names) and len(s['socket'])==len(names),'consistency','duplicate-device')
    require(set(s['regular']) <= {'bonding_masters'},'sysfs','regular-file')
    require(s['forward4'] in ('0','1') and s['forward6']=='0','forwarding','unexpected-value')
    require(s['namespace'].startswith('net:[') and s['namespace'].endswith(']'),'namespace','format')
    for x in links:
        name=x['name'];v=s['sys'][name]
        require(int(v['ifindex'])==x['index'] and (x['index'],name) in s['socket'] and int(v['type'])==x['type'], 'consistency','index-type')
        # sysfs intentionally masks volatile RUNNING/LOWER_UP/DORMANT flags.
        require(int(v['flags'],0)==x['flags'] & ~(0x40|0x10000|0x20000),'consistency','flags')
        if name=='lo':
            require(x['type']==772 and x['flags'] & 1 and x['flags'] & 8 and x['kind']=='' and not x['tunnel'],'loopback','type-state')
            require(v['operstate']=='unknown' and x['operstate']==0 and int(v['iflink'])==x['index'],'loopback','operstate-link')
        else:
            require(name in DEVICES,'links','unknown-device')
            require((x['kind'],x['type'])==DEVICES[name],'links','unknown-kind')
            require(x['flags'] in (0x80,0x1002) and not x['flags'] & 1 and x['operstate']==2 and v['operstate']=='down','links','not-down')
            require(int(v['iflink'])==0,'links','lower-link')
            require(x['tunnel']==PROFILES[x['kind']],'tunnel','unexpected-attribute-or-setting')
    idx=byname['lo']['index']
    expected={(idx,2,8,254,'127.0.0.1','127.0.0.1'),(idx,10,128,254,'::1','::1')}
    require(len(s['addresses'])==2 and set(s['addresses'])==expected,'addresses','non-loopback-or-missing')
    expected_routes={(2,8,255,2,254,idx,'127.0.0.0','127.0.0.1'),
                     (2,32,255,2,254,idx,'127.0.0.1','127.0.0.1'),
                     (2,32,255,3,253,idx,'127.255.255.255','127.0.0.1'),
                     (10,128,255,2,0,idx,'::1',None)}
    require(len(s['routes'])==4 and set(s['routes'])==expected_routes,'routes','unexpected-route')

def main():
    try:
        first=collect();validate(first)
        second=collect();validate(second)
        require(first==second,'consistency','changed-during-check')
        print('network guard PASS: cross-checks, DOWN tunnels, loopback-only addresses/routes; forwarding4='+first['forward4'])
        return 0
    except Rejected as e:
        print('STOP network stage='+e.stage+' code='+e.code,file=sys.stderr)
    except Exception:
        print('STOP network stage=collection code=read-or-parse-failed',file=sys.stderr)
    return 1

if __name__=='__main__':
    sys.exit(main())

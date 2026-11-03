"""Fixtures model the read-only LinuxKit diagnostic; tests never collect live state."""
import copy
import struct
import unittest
from unittest.mock import patch
import network_guard as g

def fixture(tunnels=True):
    links=[dict(name='lo',index=1,type=772,flags=0x10049,operstate=0,kind='',tunnel={})]
    if tunnels:
        for idx,(name,(kind,hw)) in enumerate(g.DEVICES.items(),2):
            links.append(dict(name=name,index=idx,type=hw,flags=0x1002 if kind in ('gretap','erspan') else 0x80,
                              operstate=2,kind=kind,tunnel=copy.deepcopy(g.PROFILES[kind])))
    return dict(links=links,sys={x['name']:dict(ifindex=str(x['index']),type=str(x['type']),
                    flags=hex(x['flags'] & ~(0x40|0x10000|0x20000)),operstate='unknown' if x['name']=='lo' else 'down',
                    iflink='1' if x['name']=='lo' else '0') for x in links},
                regular=['bonding_masters'],socket=[(x['index'],x['name']) for x in links],proc=[x['name'] for x in links],
                addresses=[(1,2,8,254,'127.0.0.1','127.0.0.1'),(1,10,128,254,'::1','::1')],
                routes=[(2,8,255,2,254,1,'127.0.0.0','127.0.0.1'),(2,32,255,2,254,1,'127.0.0.1','127.0.0.1'),
                        (2,32,255,3,253,1,'127.255.255.255','127.0.0.1'),(10,128,255,2,0,1,'::1',None)],
                forward4='1',forward6='0',namespace='net:[4026532558]')

class GuardTests(unittest.TestCase):
    def test_loopback_only(self): g.validate(fixture(False))
    def test_diagnostic_tunnels_and_routes(self): g.validate(fixture())
    def test_bonding_file_optional(self):
        s=fixture();s['regular']=[];g.validate(s)
    def test_forwarding_disabled_also_allowed(self):
        s=fixture();s['forward4']='0';g.validate(s)
    def bad(self,change):
        s=fixture();change(s)
        with self.assertRaises(g.Rejected): g.validate(s)
    def test_unknown_interface(self): self.bad(lambda s:s['links'][1].update(name='eth0'))
    def test_unknown_kind(self): self.bad(lambda s:s['links'][1].update(kind='veth'))
    def test_non_loopback_up(self): self.bad(lambda s:s['links'][1].update(flags=0x81))
    def test_ipv4_address(self): self.bad(lambda s:s['addresses'].append((2,2,24,0,'10.0.0.1','10.0.0.1')))
    def test_ipv6_address(self): self.bad(lambda s:s['addresses'].append((2,10,64,0,'2001:db8::1','2001:db8::1')))
    def test_external_route(self): self.bad(lambda s:s['routes'].append((2,0,254,1,0,2,'0.0.0.0',None)))
    def test_gateway_attribute(self):
        with self.assertRaises(g.Rejected): g.attrs(struct.pack('=HH',8,5)+bytes(4),{1,4,15})
    def test_other_route_table(self): self.bad(lambda s:s['routes'].append((2,8,100,2,254,1,'127.0.0.0','127.0.0.1')))
    def test_remote_endpoint(self): self.bad(lambda s:s['links'][1]['tunnel'].update({3:b'\x01\x02\x03\x04'}))
    def test_lower_link(self): self.bad(lambda s:s['sys']['tunl0'].update(iflink='3'))
    def test_sysfs_mismatch(self): self.bad(lambda s:s['sys'].pop('tunl0'))
    def test_socket_mismatch(self): self.bad(lambda s:s['socket'].pop())
    def test_proc_mismatch(self): self.bad(lambda s:s['proc'].pop())
    def test_netlink_mismatch(self): self.bad(lambda s:s['links'].pop())
    def test_unknown_tunnel_attribute(self): self.bad(lambda s:s['links'][1]['tunnel'].update({999:b'\0'}))
    def test_missing_tunnel_attribute(self): self.bad(lambda s:s['links'][1]['tunnel'].pop(3))
    def test_unknown_regular_file(self): self.bad(lambda s:s['regular'].append('unexpected'))
    def test_loopback_down(self): self.bad(lambda s:s['links'][0].update(flags=8))
    def test_loopback_wrong_type(self): self.bad(lambda s:s['links'][0].update(type=1))
    def test_missing_loopback_address(self): self.bad(lambda s:s['addresses'].pop())
    def test_operstate_not_down(self): self.bad(lambda s:s['links'][1].update(operstate=0))
    def test_sysfs_flags_mismatch(self): self.bad(lambda s:s['sys']['tunl0'].update(flags='0x81'))
    def test_forwarding_does_not_authorize_route(self): self.test_external_route()
    def test_ipv6_forwarding_unexpected(self): self.bad(lambda s:s.update(forward6='1'))
    def test_collection_failure_stops(self):
        with patch.object(g,'collect',side_effect=OSError('SECRET')),patch('sys.stderr') as err:
            self.assertEqual(g.main(),1)
            self.assertNotIn('SECRET',str(err.write.call_args_list))
    def test_parse_failure_stops(self):
        with patch.object(g,'collect',side_effect=g.Rejected('netlink','attribute-length')),patch('sys.stderr'):
            self.assertEqual(g.main(),1)
    def test_changed_state_stops(self):
        a=fixture();b=fixture();b['forward4']='0'
        with patch.object(g,'collect',side_effect=[a,b]),patch('sys.stderr'):
            self.assertEqual(g.main(),1)
    def test_normal_main(self):
        with patch.object(g,'collect',side_effect=[fixture(),fixture()]),patch('sys.stdout'):
            self.assertEqual(g.main(),0)
    def test_malformed_attributes(self):
        for raw in (b'\x01',struct.pack('=HH',3,1),struct.pack('=HH',20,1),struct.pack('=HH',4,1)*2):
            with self.subTest(raw=raw),self.assertRaises(g.Rejected):g.attrs(raw)
    def test_unknown_top_attribute(self):
        with self.assertRaises(g.Rejected):g.attrs(struct.pack('=HH',4,999),g.LINK_ATTRS)

    def test_unknown_device_with_consistent_sources(self):
        def change(s):
            s['links'][1]['name']='eth0'
            s['sys']['eth0']=s['sys'].pop('tunl0')
            s['socket'][1]=(2,'eth0');s['proc'][1]='eth0'
        self.bad(change)
    def test_down_name_does_not_authorize_wrong_type(self):
        def change(s):
            s['links'][1]['type']=1;s['sys']['tunl0']['type']='1'
        self.bad(change)
    def netlink_bad(self,data,flags=0):
        from unittest.mock import MagicMock
        sock=MagicMock();sock.__enter__.return_value=sock
        sock.recvmsg.return_value=(data,[],flags,(0,0))
        with patch.multiple(g.socket,AF_NETLINK=16,NETLINK_ROUTE=0,create=True), patch.object(g.socket,'socket',return_value=sock),self.assertRaises(g.Rejected):
            g.dump(18,bytes(16))
    def test_netlink_short_header(self): self.netlink_bad(b'x')
    def test_netlink_truncation(self): self.netlink_bad(bytes(16),g.socket.MSG_TRUNC)
    def test_netlink_interrupted_dump(self): self.netlink_bad(struct.pack('=IHHIIi',20,3,0x10,93,0,0))
    def test_netlink_error_message(self): self.netlink_bad(struct.pack('=IHHIIi',20,2,0,93,0,-1))

if __name__=='__main__': unittest.main()

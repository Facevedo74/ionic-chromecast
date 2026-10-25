from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
base = root / 'android/build/hls-relay-test'
files = {
    'android/net/Network.java': '''package android.net;
public class Network { public java.net.URLConnection openConnection(java.net.URL url) throws java.io.IOException { return url.openConnection(); } }
''',
    'com/getcapacitor/Logger.java': '''package com.getcapacitor;
public class Logger { public static void info(String t,String m){System.out.println(m);} public static void warn(String t,String m){System.out.println(m);} }
''',
    'com/fabianacevedo/ionicchromecast/RelayProbe.java': r'''package com.fabianacevedo.ionicchromecast;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
public class RelayProbe {
  static byte[] first=new byte[752],second=new byte[564],all=new byte[1316];
  static int checks=0;
  static HttpClient client=HttpClient.newHttpClient();
  static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
  static HttpResponse<byte[]> get(String url,String range,String method)throws Exception{
    var b=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(5));
    if(range!=null)b.header("Range",range);
    return client.send(b.method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
  }
  public static void main(String[] args)throws Exception{
    for(int i=0;i<first.length;i++)first[i]=(byte)(i%251);
    for(int i=0;i<second.length;i++)second[i]=(byte)(200-i%199);
    System.arraycopy(first,0,all,0,first.length);System.arraycopy(second,0,all,first.length,second.length);
    var executor=Executors.newCachedThreadPool();
    var origin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);origin.setExecutor(executor);
    origin.createContext("/",e->{try{
      String path=e.getRequestURI().getPath();boolean head=e.getRequestMethod().equals("HEAD");
      if(path.endsWith(".m3u8")){
        byte[] manifest=("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\na.ts\n#EXTINF:3,\nb.ts\n#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(200,manifest.length);e.getResponseBody().write(manifest);return;
      }
      byte[] data=path.endsWith("a.ts")?first:second;
      if(head){if(!path.startsWith("/unknown/"))e.getResponseHeaders().add("Content-Length",""+data.length);e.sendResponseHeaders(200,-1);return;}
      String range=e.getRequestHeaders().getFirst("Range");
      if(range!=null&&!path.startsWith("/ignore/")){
        String[] p=range.substring(6).split("-");int s=Integer.parseInt(p[0]),end=Integer.parseInt(p[1]);
        e.getResponseHeaders().add("Content-Range","bytes "+s+"-"+end+"/"+data.length);
        e.sendResponseHeaders(206,end-s+1);e.getResponseBody().write(data,s,end-s+1);
      }else{e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);}
    }finally{e.close();}});origin.start();
    try{
      for(String mode:new String[]{"normal","ignore","unknown"}){
        try(var relay=new RelayClose(new DlnaMediaProxy(null,InetAddress.getByName("127.0.0.1"),InetAddress.getByName("127.0.0.1"),"http://127.0.0.1:"+origin.getAddress().getPort()+"/"+mode+"/index.m3u8",true,7))){
          String url=relay.proxy.getMediaUrl();var full=get(url,null,"GET");
          check(full.statusCode()==200&&Arrays.equals(full.body(),all),mode+" full content exact");
          if(mode.equals("unknown")){check(!relay.proxy.supportsByteSeeking(),"missing HEAD size preserves chunked relay");continue;}
          check(relay.proxy.getHlsContentLength()==all.length,mode+" exact length");
          var head=get(url,null,"HEAD");check(head.body().length==0&&head.headers().firstValue("Content-Length").orElse("").equals("1316"),mode+" HEAD finite length");
          var cross=get(url,"bytes=700-900","GET");check(cross.statusCode()==206&&Arrays.equals(cross.body(),Arrays.copyOfRange(all,700,901)),mode+" range crosses segment boundary");
          var tail=get(url,"bytes=-200","GET");check(Arrays.equals(tail.body(),Arrays.copyOfRange(all,1116,1316)),mode+" suffix range");
          var open=get(url,"bytes=1000-","GET");check(Arrays.equals(open.body(),Arrays.copyOfRange(all,1000,1316)),mode+" open-ended range");
          check(get(url,"bytes=1400-","GET").statusCode()==416,mode+" unsatisfiable range");
          check(get(url,"bytes=0-1,4-8","GET").statusCode()==416,mode+" multiple ranges rejected");
        }
      }
      try(var relay=new RelayClose(new DlnaMediaProxy(null,InetAddress.getByName("127.0.0.1"),InetAddress.getByName("127.0.0.1"),"http://127.0.0.1:"+origin.getAddress().getPort()+"/normal/index.m3u8",true,0,false))){
        check(!relay.proxy.supportsByteSeeking(),"Samsung HLS uses sequential relay");
        var full=get(relay.proxy.getMediaUrl(),null,"GET");
        check(full.statusCode()==200&&Arrays.equals(full.body(),all),"Samsung HLS full content exact");
        check(full.headers().firstValue("Transfer-Encoding").orElse("").equalsIgnoreCase("chunked"),"Samsung HLS uses chunked transfer");
        check(full.headers().firstValue("Content-Duration").isEmpty(),"Samsung HLS omits duration hint");
      }
      System.out.println("SUCCESS "+checks+" relay checks");
    }finally{origin.stop(0);executor.shutdownNow();}
  }
  static class RelayClose implements AutoCloseable {final DlnaMediaProxy proxy;RelayClose(DlnaMediaProxy p){proxy=p;} public void close(){proxy.close();}}
}
'''
}
for name, content in files.items():
    p = base / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
source = root / 'android/src/main/java/com/fabianacevedo/ionicchromecast/DlnaMediaProxy.java'
classes = base / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '--add-modules', 'jdk.httpserver', '-d', str(classes), str(source), *[str(base / p) for p in files]], check=True)
subprocess.run(['java', '--add-modules', 'jdk.httpserver', '-cp', str(classes), 'com.fabianacevedo.ionicchromecast.RelayProbe'], check=True)

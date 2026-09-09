package se.steffy.reklamskydd;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

public class AdBlockVpnService extends VpnService {
    public static final String ACTION_START="se.steffy.reklamskydd.START", ACTION_STOP="se.steffy.reklamskydd.STOP", ACTION_UPDATE="se.steffy.reklamskydd.UPDATE", ACTION_STATE="se.steffy.reklamskydd.STATE";
    public static volatile boolean running=false;
    private static final String CHANNEL="protection";
    private ParcelFileDescriptor tun;
    private Thread loop;
    private final ExecutorService workers=Executors.newFixedThreadPool(4);
    private final Set<String> blocked=ConcurrentHashMap.newKeySet();

    @Override public void onCreate(){super.onCreate();createChannel();loadRules();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){stopVpn();stopSelf();return START_NOT_STICKY;}
        if(ACTION_UPDATE.equals(action)){workers.execute(this::updateRules);if(!running)stopSelf();return START_NOT_STICKY;}
        startForeground(7,notification("Skyddet är aktivt"));startVpn();return START_STICKY;
    }
    private synchronized void startVpn(){
        if(running)return;
        try{
            tun=new Builder().setSession("ReklamSkydd").setMtu(1500).addAddress("10.111.222.2",32)
                    .addDnsServer("10.111.222.1").addRoute("10.111.222.1",32).setBlocking(true).establish();
            if(tun==null){stopSelf();return;} running=true;
            getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("was_running",true).apply();broadcast();
            loop=new Thread(this::packetLoop,"ReklamSkydd-DNS");loop.start();
        }catch(Exception e){stopVpn();}
    }
    private void packetLoop(){
        try(InputStream in=new FileInputStream(tun.getFileDescriptor());OutputStream out=new FileOutputStream(tun.getFileDescriptor())){
            byte[] buf=new byte[32767];
            while(running){int n=in.read(buf);if(n>28){byte[] packet=Arrays.copyOf(buf,n);workers.execute(()->handle(packet,out));}}
        }catch(Exception ignored){} finally{stopVpn();}
    }
    private void handle(byte[] p,OutputStream out){
        try{
            int ihl=(p[0]&15)*4;if((p[0]>>4)!=4 || (p[9]&255)!=17 || p.length<ihl+20)return;
            int udp=ihl, srcPort=u16(p,udp), dns=udp+8, dnsLen=p.length-dns;if(dnsLen<12)return;
            String host=qname(p,dns+12).toLowerCase(Locale.ROOT);
            byte[] answer;
            if(isBlocked(host)){answer=blockedResponse(Arrays.copyOfRange(p,dns,p.length));increment();}
            else answer=resolve(Arrays.copyOfRange(p,dns,p.length));
            if(answer==null)return;
            byte[] response=udpPacket(p,ihl,srcPort,answer);
            synchronized(out){out.write(response);out.flush();}
        }catch(Exception ignored){}
    }
    private byte[] resolve(byte[] query){
        DatagramSocket socket=null;
        try{socket=new DatagramSocket();if(!protect(socket)){socket.close();return null;}socket.setSoTimeout(3500);
            socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("1.1.1.1"),53));byte[] b=new byte[4096];DatagramPacket r=new DatagramPacket(b,b.length);socket.receive(r);return Arrays.copyOf(b,r.getLength());
        }catch(Exception e){return null;}finally{if(socket!=null)socket.close();}
    }
    private boolean isBlocked(String host){
        String h=host;while(!h.isEmpty()){if(blocked.contains(h))return true;int dot=h.indexOf('.');if(dot<0)break;h=h.substring(dot+1);}return false;
    }
    private byte[] blockedResponse(byte[] q){
        byte[] r=Arrays.copyOf(q,q.length);r[2]=(byte)0x81;r[3]=(byte)0x83;r[6]=r[7]=r[8]=r[9]=r[10]=r[11]=0;return r;
    }
    private byte[] udpPacket(byte[] request,int ihl,int clientPort,byte[] dns){
        int total=20+8+dns.length;byte[] r=new byte[total];r[0]=0x45;r[1]=0;r[2]=(byte)(total>>8);r[3]=(byte)total;r[6]=0x40;r[8]=64;r[9]=17;
        System.arraycopy(request,16,r,12,4);System.arraycopy(request,12,r,16,4);
        r[20]=0;r[21]=53;r[22]=(byte)(clientPort>>8);r[23]=(byte)clientPort;int ul=8+dns.length;r[24]=(byte)(ul>>8);r[25]=(byte)ul;
        System.arraycopy(dns,0,r,28,dns.length);put16(r,10,checksum(r,0,20));
        long sum=0;for(int i=12;i<20;i+=2)sum+=u16(r,i);sum+=17+ul;for(int i=20;i+1<r.length;i+=2)sum+=u16(r,i);if((r.length&1)==1)sum+=(r[r.length-1]&255)<<8;while((sum>>16)>0)sum=(sum&65535)+(sum>>16);put16(r,26,(int)(~sum)&65535);return r;
    }
    private int checksum(byte[] b,int off,int len){long s=0;for(int i=off;i<off+len;i+=2)s+=u16(b,i);while((s>>16)>0)s=(s&65535)+(s>>16);return(int)(~s)&65535;}
    private int u16(byte[] b,int i){return((b[i]&255)<<8)|(b[i+1]&255);}private void put16(byte[] b,int i,int v){b[i]=(byte)(v>>8);b[i+1]=(byte)v;}
    private String qname(byte[] b,int pos){StringBuilder s=new StringBuilder();while(pos<b.length){int n=b[pos++]&255;if(n==0)break;if(n>63||pos+n>b.length)return"";if(s.length()>0)s.append('.');s.append(new String(b,pos,n,StandardCharsets.ISO_8859_1));pos+=n;}return s.toString();}
    private void increment(){SharedPreferences p=getSharedPreferences("settings",MODE_PRIVATE);p.edit().putLong("blocked",p.getLong("blocked",0)+1).apply();broadcast();}
    private void loadRules(){blocked.clear();try{readRules(getAssets().open("blocklist.txt"));}catch(Exception ignored){}try{readRules(openFileInput("downloaded.txt"));}catch(Exception ignored){}}
    private void readRules(InputStream input)throws IOException{try(BufferedReader br=new BufferedReader(new InputStreamReader(input))){String line;while((line=br.readLine())!=null){line=line.trim().toLowerCase(Locale.ROOT);if(line.isEmpty()||line.startsWith("#"))continue;String[] a=line.split("\\s+");String h=a.length>1?a[a.length-1]:a[0];if(!h.equals("localhost")&&!h.contains("#"))blocked.add(h);}}}
    private void updateRules(){
        File tmp=new File(getFilesDir(),"downloaded.tmp");
        String[] sources={
                "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        };
        boolean wrote=false;
        try(OutputStream out=new FileOutputStream(tmp)){
            for(String source:sources){
                try{URL u=new URL(source);HttpsURLConnection c=(HttpsURLConnection)u.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","ReklamSkydd/1.1");
                    try(InputStream in=c.getInputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))>0){out.write(b,0,n);wrote=true;}out.write('\n');}
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}
        File dst=new File(getFilesDir(),"downloaded.txt");
        if(wrote){if(dst.exists())dst.delete();if(tmp.renameTo(dst))loadRules();}else tmp.delete();
    }
    private synchronized void stopVpn(){running=false;if(loop!=null)loop.interrupt();try{if(tun!=null)tun.close();}catch(Exception ignored){}tun=null;getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("was_running",false).apply();broadcast();stopForeground(STOP_FOREGROUND_REMOVE);}
    private void broadcast(){sendBroadcast(new Intent(ACTION_STATE).setPackage(getPackageName()));}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Reklamskydd",NotificationManager.IMPORTANCE_LOW);c.setDescription("Visar när reklamblockeringen är aktiv");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_shield).setContentTitle("ReklamSkydd").setContentText(text).setOngoing(true).setContentIntent(pi).build();}
    @Override public void onDestroy(){stopVpn();workers.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return super.onBind(intent);}
}

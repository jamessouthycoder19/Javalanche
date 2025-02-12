package Servers;

import Servers.Beacon.BeaconServer;
import Servers.C2.C2Server;

public class notifyLock implements Runnable{
    // Lock that will be notified
    private Object lock;

    // Pointer to whichever Class is using this class
    private C2Server C2ServerPointer;
    private BeaconServer BeaconServerPointer;

    // This ID is used to determine if .notify() should still be called or not
    private int shellID;

    // 2 Constructors, one if the class is being created by a C2 Server, one if the class is being created by a Beacon Server 
    public notifyLock(Object lock, C2Server C2ServerPointer, int shellID){
        this.lock = lock;
        this.C2ServerPointer = C2ServerPointer;
        this.BeaconServerPointer = null;
        this.shellID = shellID;
    }

    public notifyLock(Object lock, BeaconServer beaconServerPointer, int shellID){
        this.lock = lock;
        this.C2ServerPointer = null;
        this.BeaconServerPointer = beaconServerPointer;
        this.shellID = shellID;
    }

    @Override
    public void run() {
        // Sleep for 10 seconds
        try{
            Thread.sleep(10000);
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        
        // notify the lock
        if (C2ServerPointer != null){
            if(shellID == C2ServerPointer.getShellID()){
                synchronized(lock){
                    lock.notify();
                }
            }
        } else {
            if(shellID == BeaconServerPointer.getShellID()){
                synchronized(lock){
                    lock.notify();
                }
            }
        }   
    }
}

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Bank {
    private static final int NUM_CUSTOMERS = 2;
    private static final int NUM_TELLERS = 1;
    private static final Semaphore DOOR = new Semaphore(2);  
    private static final LinkedList<Interaction> queue = new LinkedList<>();
    private static final Object QUEUE = new Object();
    private static final Semaphore AVAILABLE = new Semaphore(0);
    private static final AtomicInteger SERVED = new AtomicInteger(0);



  static public void main(String[] args) throws InterruptedException {
    List<Teller> tellers = new ArrayList<>();
        


 }
 static class Interaction{

}

  static class Teller extends Thread {
        
        }


}
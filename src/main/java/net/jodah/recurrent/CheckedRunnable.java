package net.jodah.recurrent;

/**
 * A Runnable that throws checked exceptions.
 * 
 * @author Jonathan Halterman
 */
public interface CheckedRunnable {
  void run() throws Exception;
}

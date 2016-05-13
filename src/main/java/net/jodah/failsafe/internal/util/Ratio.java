package net.jodah.failsafe.internal.util;

/**
 * A ratio.
 * 
 * @author Jonathan Halterman
 */
public class Ratio {
  public final int numerator;
  public final int denominator;
  public final double ratio;

  public Ratio(int numerator, int denominator) {
    this.numerator = numerator;
    this.denominator = denominator;
    ratio = (double) numerator / (double) denominator;
  }
}

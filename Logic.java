
/** Errors.java is a support class used for warnings and fatal error reporting.
 *  Code here produces error messages with a standard prefix and general format.
 *  @author Douglas W. Jones
 *  @author Basil Chatha
 *  @version 2017-12-04 MP6
 *  Adapted from Logic.java Version 2017-10-30 (the MP4 solution).
 *
 *  Bug notices in the code indicate unsolved problems
 */

import java.util.LinkedList;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.PriorityQueue;

class Errors {
    // error messages are counted.
    private static int errorCount = 0;

    /** Allow public read-only access to the count of error messages
     * @return the count
     */
    public static int count() {
        return errorCount;
    }

    /** Report nonfatal errors, output a message and return
     * @param message the message to output
     */
    public static void warn( String message ) {
        System.err.println( "Logic: " + message );
        errorCount = errorCount + 1;
    }

    /** Report fatal errors, output a message and exit, never to return
     * @param message the message to output
     */
    public static void fatal( String message ) {
        warn( message );
        System.exit( 1 );
    }
}
/** Gate.java
 * Representations of logic gates in class Gate and its subsidiaries
 * author Douglas W. Jones
 * author Basil Chatha
 * version 2017-12-04 MP6
 * Adapted from Logic.java Version 2017-10-30 (the MP4 solution),
 *
 * Bug notices in the code indicate unsolved problems
 */

/** Gates process inputs from Wires and deliver outputs to Wires
 *  @see Wire
 *  @see AndGate
 *  @see OrGate
 *  @see NotGate
 *  @see ConstGate
 */

abstract class Gate {
    // constructors may throw this when an error prevents construction
    public static class ConstructorFailure extends Exception {}

    // fields of a gate
    public final String name;            // textual name of gate, never null!
    protected final float delay;         // the delay of this gate, in seconds

    // information about gate connections and logic values is all in subclasses

    /** Constructor used only from within subclasses of class Gate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    protected Gate( String name, float delay ) {
        this.name = name;
        this.delay = delay;
    }

    /** The public use this factory to construct different gates
     *  @param sc the scanner from which the textual gate description is read
     *  @return the newly constructed gate
     *  @throws ConstructorFailure when a new gate cannot be constructed
     */
    public final static Gate factory( Scanner sc ) throws ConstructorFailure {
        // tempraries used while constructing a gate
        final String name;
        final String kind;
        final float delay;
        final Gate newGate;

        // scan basic fields of input line
        try {
            name = ScanSupport.nextName(
                    sc, ()->"gate ???"
            );
            kind = ScanSupport.nextName(
                    sc, ()->"gate " + name + " ???"
            );
            delay = ScanSupport.nextFloat(
                    sc, ()->"gate " + name + " " + kind + " ???"
            );
        } catch (ScanSupport.NotFound e) {
            throw new ConstructorFailure();
        }

        // check the fields
        if (Logic.findGate( name ) != null) {
            Errors.warn( "Redefinition: gate " + name + " " + kind );
            sc.nextLine();
            throw new ConstructorFailure();
        }

        if (delay < 0.0F) Errors.warn(
                "Negative delay: " + "gate " + name + " " + kind + " " + delay
                // don't throw a failure here, we can build a gate with this error
        );

        // now construct the right kind of gate
        if ("and".equals( kind )) {
            newGate = new AndGate( name, delay );
        } else if ("or".equals( kind )) {
            newGate = new OrGate( name, delay );
        } else if ("not".equals( kind )) {
            newGate = new NotGate( name, delay );
        } else if ("const".equals( kind )) {
            newGate = new ConstGate( name, delay );
        } else {
            Errors.warn( "Unknown gate kind: gate " + name + " " + kind );
            sc.nextLine();
            throw new ConstructorFailure();
        }

        ScanSupport.lineEnd( sc, ()->newGate.toString() );
        return newGate;
    }

    /** tell the gate that one of its input pins is in use
     *  @param w the wire that is connected
     *  @param pinName the text of a pin name
     *  @return a pin number usable as a parameter to inPinName
     */
    public abstract int registerInput( Wire w, String pinName );

    /** tell the gate that one of its output pins is in use
     *  @param w the wire that is connected
     *  @param pinName the text of a pin name
     *  @return a pin number usable as a parameter to outPinName
     */
    public abstract int registerOutput( Wire w, String pinName );

    /** get the name of the input pin, given its corresponding number
     *  @param pinNumber a pin number previously returned by {@link #registerInput(Wire, String)}
     *  @return pinName the textual name of an input pin
     */
    public abstract String inPinName( int pinNumber );

    /** get the name of the output pin, given its corresponding number
     *  @param pinNumber a pin number previously returned by {@link #registerOutput(Wire, String)}
     *  @return pinName the textual name of an output pin
     */
    public abstract String outPinName( int pinNumber );

    /** check the sanity of this gate's connections
     */
    public abstract void checkSanity();

    // Simulation methods

    /** simulate the change of one of this gate's inputs
     *  @param time the time when the input changes
     *  @param dstPin the pin that changes
     *  @param v the new logic value
     */
    public abstract void inputChangeEvent( float time, int dstPin, boolean v );

} // abstract class Gate

/** Gathers all of the properties common to single-output gates
 *  Specifically, all LogicGates drive a single list of output wires
 *  with a single output value when an OutputChangeEvent occurs.
 *  @see AndGate
 *  @see OrGate
 *  @see NotGate
 */
abstract class LogicGate extends Gate {
    // set of all wires out of this gate
    private LinkedList <Wire> outgoing = new LinkedList <Wire> ();

    // this gate's value, computed by input change events
    protected boolean value = false;

    // this gate's most recent actual output value
    private boolean outValue = false;

    /** The constructor used only from subclasses of LogicGate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    public LogicGate( String name, float delay ) {
        super( name, delay );
    }

    /** tell the gate that one of its output pins is in use
     *  @param w the wire that is connected
     *  @param pinName name of output pin being registered
     *  @return corresponding pin number
     */
    public final int registerOutput( Wire w, String pinName ) {
        if ("out".equals( pinName )) {
            outgoing.add( w );
            return 0;
        } else {
            Errors.warn( "Illegal output pin: " + name + " " + pinName );
            return -1;
        }
    }

    /** get the name of the output pin, given its number
     *  @param pinNumber number of output pin corresponding to its name
     *  @return pinName
     */
    public final String outPinName( int pinNumber ) {
        if (pinNumber == 0) return "out";
        return "???";
    }

    // Simulation methods

    /** Simulate an output change on this wire
     *  @param time tells when this wire's input changes
     *  Passes the new value to the input of the gate to which this wire goes.
     *  Uses the this.value field to determine the new output value.
     *  Output change events are scheduled (directly or indirectly) by the
     *  input change event of the actual gate object.
     *  @see #inputChangeEvent(float, int, boolean)
     */
    protected final void outputChangeEvent( float time ) {
        if (value != outValue) { // only if the output actually changes
            outValue = value;
            System.out.println(
                    "At " + time + " " + toString() +
                            " out " + " changes to " + value
            );
            for (Wire w: outgoing) {
                w.inputChangeEvent( time, value );
            }
        }
    }

} // abstract class LogicGate

/** Handles the properties common to logic gates with two inputs
 *  Specifically, all two-input gates have two input wires, in1 an in2.
 *  @see AndGate
 *  @see OrGate
 *  @see LogicGate
 */
abstract class TwoInputGate extends LogicGate {
    // usage records for inputs
    protected boolean in1used = false;
    protected boolean in2used = false;

    // Boolean values of inputs
    protected boolean in1 = false;
    protected boolean in2 = false;

    /** The constructor used only from subclasses of TwoInputGate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    protected TwoInputGate( String name, float delay ) {
        super( name, delay );
    }

    /** tell the gate that one of its input pins is in use
     *  @param w the wire that is connected
     *  @param pinName name of input pin being registered
     *  @return corresponding pin number, else return -1
     */
    public final int registerInput( Wire w, String pinName ) {
        if ("in1".equals( pinName )) {
            if (in1used) Errors.warn(
                    "Multiple uses of input pin: " + name + " in1"
            );
            in1used = true;
            return 1;
        } else if ("in2".equals( pinName )) {
            if (in2used) Errors.warn(
                    "Multiple uses of input pin: " + name + " in2"
            );
            in2used = true;
            return 2;
        } else {
            Errors.warn( "Illegal input pin: " + name + " " + pinName );
            return -1;
        }
    }

    /** get the name of the input pin, given its number
     * @param pinNumber number of input pin corresponding to its name
     * @return corresponding pin name, else return '???'
     */
    public final String inPinName( int pinNumber ) {
        if (pinNumber == 1) return "in1";
        if (pinNumber == 2) return "in2";
        return "???";
    }

    /** check the sanity of this gate's connections
     */
    public final void checkSanity() {
        if (!in1used) Errors.warn( "Unused input pin: " + name + " in1" );
        if (!in2used) Errors.warn( "Unused input pin: " + name + " in2" );
    }

    // Simulation methods

    /** update the output value of a gate based on its input values
     *  This is called from inputChangeEvent to delegate the response of the
     *  logic gate to the input change to the actual gate instead of this
     *  abstract class.
     *  @param time the value is updated
     */
    public abstract void updateValue( float time );

    /** simulate the change of one of this gate's inputs
     *  @param time the time when the input changes
     *  @param dstPin the pin that changes
     *  @param v the new logic value
     */
    public void inputChangeEvent( float time, int dstPin, boolean v ) {
        if (dstPin == 1) {
            in1 = v;
        } else if (dstPin == 2) {
            in2 = v;
        }
        updateValue( time );
    }

} // abstract class TwoInputGate

/** Handles the properties specific to and gates.
 *  @see TwoInputGate
 *  @see LogicGate
 */
final class AndGate extends TwoInputGate {

    /** The constructor used only from within class Gate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    public AndGate( String name, float delay ) {
        super( name, delay );
    }

    /** reconstruct the textual description of this gate
     *  @return the textual description
     */
    public String toString() {
        return "gate " + name + " and " + delay;
    }

    // Simulation methods

    /** update the output value of a gate based on its input values.
     *  update to false if at least one input is now false.
     *  update to true if both inputs are now true.
     *  if the value has changed, schedule a {@link #outputChangeEvent(float)}
     *  @param time the value is updated
     */
    public void updateValue( float time ) {
        boolean newVal = in1 & in2;
        if (newVal != value) {
            value = newVal;
            Simulator.schedule(
                    new Simulator.Event( time + (delay * 0.95f) +
                                         PRNG.randomFloat( delay * 0.1f )) {
                        void trigger() {
                            outputChangeEvent( time );
                        }
                    }
            );
        }
    }

} // class AndGate

/** Handles the properties specific to or gates.
 *  @see TwoInputGate
 *  @see LogicGate
 */
final class OrGate extends TwoInputGate {

    /** The constructor used only from within class Gate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    public OrGate( String name, float delay ) {
        super( name, delay );
    }

    /** reconstruct the textual description of this gate
     *  @return the textual description
     */
    public String toString() {
        return "gate " + name + " or " + delay;
    }

    // Simulation methods

    /** update the output value of a gate based on its input values.
     *  update to false if both inputs are now false.
     *  update to true if both inputs are now true.
     *  if the value has changed, schedule a {@link #outputChangeEvent(float)}
     *  @param time the value is updated
     */
    public void updateValue( float time ) {
        boolean newVal = in1 | in2;
        if (newVal != value) {
            value = newVal;
            Simulator.schedule(
                    new Simulator.Event( time + (delay * 0.95f) +
                                         PRNG.randomFloat( delay * 0.1f )) {
                        void trigger() {
                            outputChangeEvent( time );
                        }
                    }
            );
        }
    }

} // class OrGate

/** Handles the properties specific to not gates.
 *  @see LogicGate
 */
final class NotGate extends LogicGate {
    // usage records for inputs
    private boolean inUsed = false;

    /** The constructor used only from within class Gate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    public NotGate( String name, float delay ) {
        super( name, delay );
    }

    /** tell the gate that one of its input pins is in use
     *  @param w the wire that is connected
     *  @param pinName name of input pin being registered
     *  @return corresponding pin number, else return -1
     */
    public int registerInput( Wire w, String pinName ) {
        if ("in".equals( pinName )) {
            if (inUsed) Errors.warn(
                    "Multiple uses of input pin: " + name + " in"
            );
            inUsed = true;
            return 0;
        } else {
            Errors.warn( "Illegal input pin: " + name + " " + pinName );
            return -1;
        }
    }

    /** get the name of the input pin, given its number
     * @param pinNumber number of input pin corresponding to its name
     * @return corresponding pin name, else return '???'
     */
    public String inPinName( int pinNumber ) {
        if (pinNumber == 0) return "in";
        return "???";
    }

    /** check the sanity of this gate's connections.
     *  launch the simulation from here - schedule a {@link #outputChangeEvent(float)}
     */
    public void checkSanity() {
        if (!inUsed) Errors.warn( "Unused input pin: " + name + " in" );

        // this is a good time to launch the simulation
        value = true;
        Simulator.schedule(
                new Simulator.Event( delay ) {
                    void trigger() {
                        outputChangeEvent( time );
                    }
                }
        );
    }

    /** reconstruct the textual description of this gate
     *  @return the textual description
     */
    public String toString() {
        return "gate " + name + " not " + delay;
    }

    // Simulation methods

    /** simulate the change of one of this gate's inputs
     *  if the inputs have changed, schedule a {@link #outputChangeEvent(float)}
     *  @param t the time when the input changes
     *  @param dstPin the pin that changes
     *  @param v the new logic value
     */
    public void inputChangeEvent( float t, int dstPin, boolean v ) {
        value = !v;
        Simulator.schedule(
                new Simulator.Event( t + (delay * 0.95f) +
                                     PRNG.randomFloat( delay * 0.1f ) ) {
                    void trigger() {
                        outputChangeEvent( time );
                    }
                }
        );
    }

} // class NotGate

/** Handles the properties specific to const gates.
 */
final class ConstGate extends Gate {
    // set of all wires out of this gate
    private LinkedList <Wire> outgoingTrue = new LinkedList <Wire> ();
    private LinkedList <Wire> outgoingFalse = new LinkedList <Wire> ();

    /** The constructor used only from within class Gate
     *  @param name used to initialize the final field
     *  @param delay used to initialize the final field
     */
    public ConstGate( String name, float delay ) {
        super( name, delay );
    }

    /** tell the gate that one of its input pins is in use.
     *  Const gates don't have input pins so warn of illegal input
     *  @param w the wire that is connected
     *  @param pinName name of input pin being registered
     *  @return -1, otherwise warn of illegal input
     */
    public int registerInput( Wire w, String pinName ) {
        Errors.warn( "Illegal input pin: " + name + " " + pinName );
        return -1;
    }

    /** tell the gate that one of its output pins is in use
     *  @param w the wire that is connected
     *  @param pinName name of output pin being registered
     *  @return corresponding pin number, else return -1
     */
    public int registerOutput( Wire w, String pinName ) {
        if ("true".equals( pinName )) {
            outgoingTrue.add( w );
            return 1;
        } else if ("false".equals( pinName )) {
            outgoingFalse.add( w );
            return 0;
        } else {
            Errors.warn( "Illegal output pin: " + name + " " + pinName );
            return -1;
        }
    }

    /** get the name of the input pin, given its number
     * @param pinNumber number of input pin corresponding to its name
     * @return '???', because Const gates should not have input pins
     */
    public String inPinName( int pinNumber ) {
        return "???";
    }

    /** get the name of the output pin, given its number
     * @param pinNumber number of output pin corresponding to its name
     * @return corresponding output pin name, else return '???'
     */
    public String outPinName( int pinNumber ) {
        if (pinNumber == 0) return "false";
        if (pinNumber == 1) return "true";
        return "???";
    }

    /** check the sanity of this gate's connections.
     *  launch the simulation from here - schedule a {@link #outputChangeEvent(float)}
     */
    public void checkSanity() {
        // no sanity check; there are no input pins to check

        // this is a good time to launch the simulation
        Simulator.schedule(
                new Simulator.Event( delay ) {
                    void trigger() {
                        outputChangeEvent( time );
                    }
                }
        );
    }

    /** reconstruct the textual description of this gate
     *  @return the textual description
     */
    public String toString() {
        return "gate " + name + " const " + delay;
    }

    // Simulation methods

    /** simulate the change of one of this gate's inputs.
     *  The Const gate's inputs should never change because it doesn't have any.
     *  @param time the time when the input changes
     *  @param dstPin the pin that changes
     *  @param v the new logic value
     */
    public void inputChangeEvent( float time, int dstPin, boolean v ) {
        Errors.fatal( "Input should never change: " + toString() );
    }

    /** simulate the change of this gate's output, then change the value of
     *  all wires connected to the output of this gate by calling
     *  {@link Wire#inputChangeEvent(float, boolean)}
     * @param time the time when the output changes
     */
    public void outputChangeEvent( float time ) {
        System.out.println(
                "At " + time + " " + toString() + " true " + " changes to true"
        );
        for (Wire w: outgoingTrue) {
            w.inputChangeEvent( time, true );
        }
    }

} // class ConstGate

/** Logic.java
 * Main class for a program to process description of a logic circuit
 * author Douglas W. Jones
 * version 2017-11-15 (MP5 solution)
 * Adapted from Logic.java Version 2017-10-20 (the MP4 solution),
 *
 * Bug notices in the code indicate unsolved problems
 */

/** The main class, orchestrates the building and simulation of a logic circuit.
 *  Logic circuits consist of a collection of gates connected by wires.
 *  Logic circuits are built using tools in class ScanSupport, with
 *  error reporting using class Errors.
 *  The actual simulation is done by Simulator.run()
 *  @see Simulator
 *  @see Wire
 *  @see Gate
 *  @see ScanSupport
 *  @see Errors
 */
public class Logic {

    // the sets of all wires and all gates
    private static LinkedList <Wire> wires
            = new LinkedList <Wire> ();
    private static LinkedList <Gate> gates
            = new LinkedList <Gate> ();

    /** Find a gate by textual name in the set gates
     *  @param s name of a gate
     *  @return the gate named s or null if none
     */
    public static Gate findGate( String s ) {
        // quick and dirty implementation
        for (Gate i: gates) {
            if (i.name.equals( s )) {
                return i;
            }
        }
        return null;
    }

    /** Initialize this logic circuit by scanning its description
     * @param sc the scanner from which end of line is scanned
     */
    private static void readCircuit( Scanner sc ) {
        while (sc.hasNext()) {
            String command = sc.next();
            if ("gate".equals( command )) {
                try {
                    gates.add( Gate.factory( sc ) );
                } catch (Gate.ConstructorFailure e) {
                    // do nothing, the constructor already reported the error
                }
            } else if ("wire".equals( command )) {
                try {
                    wires.add( new Wire( sc ) );
                } catch (Wire.ConstructorFailure e) {
                    // do nothing, the constructor already reported the error
                }
            } else if ("--".equals( command )) {
                sc.nextLine();
            } else {
                Errors.warn( "unknown command: " + command );
                sc.nextLine();
            }
        }
    }

    /** Check that a circuit is properly constructed
     */
    private static void sanityCheck() {
        for (Gate i: gates) i.checkSanity();
        // Bug: Are there any sensible sanity checks on wires?
    }

    /** Print out the wire network to system.out
     */
    private static void printCircuit() {
        for (Gate i: gates) {
            System.out.println( i.toString() );
        }
        for (Wire r: wires) {
            System.out.println( r.toString() );
        }
    }

    /** Main program
     * @param args file input names
     */
    public static void main( String[] args ) {
        if (args.length < 1) {
            Errors.fatal( "Missing file name argument" );
        } else if (args.length > 1) {
            Errors.fatal( "Too many arguments" );
        } else try {
            readCircuit( new Scanner( new File( args[0] ) ) );
            sanityCheck();
            if (Errors.count() == 0) Simulator.run();
            // note that writeCircuit is no longer called anywhere
        } catch (FileNotFoundException e) {
            Errors.fatal( "Can't open the file" );
        }
    }
}

/** PRNG.java
 * support class for pseudo-random number generation
 * author Douglas W. Jones
 * version 2017-11-15 (MP5 solution)
 * Adapted from Logic.java Version 2017-10-30 (the MP4 solution).
 *
 * Bug notices in the code indicate unsolved problems
 */

/** Pseudo Random Number Generator
 *  needed to make a single global stream of numbers, hiding Java's failures
 */
class PRNG {
    private static Random stream = new Random( 29 );
    // Bug:  For debugging, use a known seed so errors are reproducable

    /** get a number n where 0 less than or equal to n  and less than bound
     *  @param bound the boundery between it and zero from which to generate
     *               a random integer
     *  @return n
     */
    public static int fromZeroTo( int bound ) {
        return stream.nextInt( bound );
    }

    /** get a floating point number x such that 0 less than or equal to
     *  n and less than bound
     *  @param f the percentage you would like from the random float generated
     *  @return x
     */
    public static float randomFloat( float f ) {
        return stream.nextFloat() * f;
    }
}

/** ScanSupport.java
 * A support class for scanning input files
 * author Douglas W. Jones
 * version 2017-11-15 (MP5 solution)
 * Adapted from Logic.java Version 2017-10-30 (the MP4 solution).
 *
 * Class ScanSupport taken from RoadNetwork.java Version 2017-10-25
 *
 * Bug notices in the code indicate unsolved problems
 */

/** Support methods for scanning
 * @see Errors
 */
class ScanSupport {
    // exception thrown to indicate failure
    public static class NotFound extends Exception {}

    // patterns needed for scanning
    private static final Pattern name
            = Pattern.compile( "[a-zA-Z0-9_]*" );
    private static final Pattern intPattern
            = Pattern.compile( "-?[0-9][0-9]*|");
    private static final Pattern floatPattern
            = Pattern.compile( "-?[0-9][0-9]*\\.?[0-9]*|\\.[0-9][0-9]*|");
    private static final Pattern whitespace
            = Pattern.compile( "[ \t]*" ); // no newlines

    // interface for passing error messages
    public static interface Message {
        public String myString();
    }

    /** Get next name without skipping to next line (unlike sc.Next())
     *  @param sc the scanner from which end of line is scanned
     *  @param m the context part of the missing name error message
     *  @return the name if there was one.
     *  @throws NotFound if there wasn't one
     */
    public static String nextName( Scanner sc, Message m ) throws NotFound {
        sc.skip( whitespace );
        sc.skip( name );
        String s = sc.match().group();
        if ("".equals( s )) {
            Errors.warn( "name expected: " + m.myString() );
            sc.nextLine();
            throw new NotFound();
        }
        return s;
    }

    /** Get next int without skipping to next line (unlike sc.nextInt())
     *  @param sc the scanner from which end of line is scanned
     *  @param m the message to output if there was no int
     *  @return the value if there was one
     *  @throws NotFound if there wasn't one
     */
    public static int nextInt( Scanner sc, Message m ) throws NotFound {
        sc.skip( whitespace );
        sc.skip( intPattern );
        String s = sc.match().group();
        if ("".equals( s )) {
            Errors.warn( "Float expected: " + m.myString() );
            sc.nextLine();
            throw new NotFound();
        }
        // now, s is guaranteed to hold a legal int
        return Integer.parseInt( s );
    }

    /** Get next float without skipping to next line (unlike sc.nextFloat())
     *  @param sc the scanner from which end of line is scanned
     *  @param m the message to output if there was no float
     *  @return the value if there was one
     *  @throws NotFound if there wasn't one
     */
    public static float nextFloat( Scanner sc, Message m ) throws NotFound {
        sc.skip( whitespace );
        sc.skip( floatPattern );
        String s = sc.match().group();
        if ("".equals( s )) {
            Errors.warn( "Float expected: " + m.myString() );
            sc.nextLine();
            throw new NotFound();
        }
        // now, s is guaranteed to hold a legal float
        return Float.parseFloat( s );
    }

    /** Advance to next line and complain if is junk at the line end
     *  @see Errors
     *  @param sc the scanner from which end of line is scanned
     *  @param message gives a prefix to give context to error messages
     *  This version supports comments starting with --
     */
    public static void lineEnd( Scanner sc, Message message ) {
        sc.skip( whitespace );
        String lineEnd = sc.nextLine();
        if ( (!lineEnd.equals( "" ))
                &&   (!lineEnd.startsWith( "--" )) ) {
            Errors.warn(
                    message.myString() +
                            " followed unexpected by '" + lineEnd + "'"
            );
        }
    }
} // class ScanSupport

/** Simulator.java
 * Support package for discfrete-event simulation
 * author Douglas W. Jones
 * version 2017-11-15 (MP5 solution)
 * Adapted from Logic.java Version 2017-10-30 (the MP4 solution),
 *
 * Class Simulator taken from RoadNetwork.java Version 2017-10-25
 *
 * Bug notices in the code indicate unsolved problems
 */

/** Framework for discrete event simulation
 *  @author Douglas Jones
 *  @version 4/18/2016
 */
class Simulator {

    /** Users create subclasses of event to schedule anything
     */
    public static abstract class Event {
        protected final float time; // the time of this event

        public Event( float t ) {
            time = t;               // initializer
        }

        abstract void trigger();    // what to do at that time
    }

    private static PriorityQueue <Event> eventSet
            = new PriorityQueue <Event> (
            (Event e1, Event e2) -> Float.compare( e1.time, e2.time )
    );

    /** Call schedule to make act happen at time.
     * @param e the event being scheduled
     */
    public static void schedule( Event e ) {
        eventSet.add( e );
    }

    /** Call run() after scheduling some initial events
     *  to run the simulation.
     */
    public static void run() {
        while (!eventSet.isEmpty()) {
            Event e = eventSet.remove();
            e.trigger();
        }
    }
}

/** Wire.java
 * Class representing wires in description and simulation of a logic circuit.
 * author Douglas W. Jones
 * version 2017-11-15 (MP5 solution)
 * Adapted from Logic.java Version 2017-10-30 (the MP4 solution),
 *
 * Bug notices in the code indicate unsolved problems
 */

/** Wires join Gates
 *  @see Gate
 */
class Wire {
    // constructors may throw this when an error prevents construction
    public static class ConstructorFailure extends Exception {}

    // fields of a gate
    private final float delay;        // measured in seconds
    private final Gate source;        // where this wire comes from, never null
    private final int srcPin;         // what pin number of source
    private final Gate destination;   // where this wire goes, never null
    private final int dstPin;         // what pin number of destination
    // note, wires don't understand pin numbers, only gates do.
    // note, by convention -1 is an illegal pin number.

    /** construct a new wire by scanning its description from the source file
     *  @param sc the scanner from which the wire description is scanned
     *  @see ScanSupport for the tools used to access the scanner
     *  @throws ConstructorFailure when a new wire cannot be constructed
     */
    public Wire( Scanner sc ) throws ConstructorFailure {
        // temporaries used during construction
        final String sourceName;
        final String srcPinName;
        final String dstName;
        final String dstPinName;

        // pick off the text fields of the source line
        try {
            sourceName = ScanSupport.nextName(
                    sc, ()-> "wire ???"
            );
            srcPinName = ScanSupport.nextName(
                    sc, ()->"wire " + sourceName + " ???"
            );
            dstName = ScanSupport.nextName(
                    sc, ()->"wire " + " " + srcPinName + " ???"
            );
            dstPinName = ScanSupport.nextName(
                    sc, ()->"wire " + " " + srcPinName + " " + dstName + " ???"
            );
        } catch (ScanSupport.NotFound e) {
            throw new ConstructorFailure();
        }

        source = Logic.findGate( sourceName );
        destination = Logic.findGate( dstName );
        if (source == null) {
            Errors.warn( "No such source gate: wire "
                    + sourceName + " " + srcPinName + " "
                    + dstName + " " + dstPinName
            );
            sc.nextLine();
            throw new ConstructorFailure();
        }
        if (destination == null) {
            Errors.warn( "No such destination gate: wire "
                    + sourceName + " " + srcPinName + " "
                    + dstName + " " + dstPinName
            );
            sc.nextLine();
            throw new ConstructorFailure();
        }

        // take care of source and destination pins
        // Bug:  This is a start, but in the long run, it might not be right
        srcPin = source.registerOutput( this, srcPinName );
        dstPin = destination.registerInput( this, dstPinName );

        // pick off the numeric field of the source line
        try {
            delay = ScanSupport.nextFloat(
                    sc, ()->"wire "
                            + sourceName + " " + srcPinName + " "
                            + dstName + " " + dstPinName + " ???"
            );
        } catch (ScanSupport.NotFound e) {
            throw new ConstructorFailure();
        }
        if (delay < 0.0F) Errors.warn( "Negative delay: " + this.toString() );

        ScanSupport.lineEnd( sc, ()->this.toString() );
    }

    /** get textual description of a wire in a form like that used for input
     * @return the textual form
     */
    public String toString() {
        return  "wire "
                + source.name + " "
                + source.outPinName( srcPin ) + " "
                + destination.name + " "
                + destination.inPinName( dstPin ) + " "
                + delay;
    }

    // Simulation methods

    /** Simulate an input change on this wire
     *  @param t tells when this wire's input changes
     *  @param v gives the new value on this wire
     *  schedules an output change event after the wire's delay.
     *  @see #outputChangeEvent(float, boolean)
     */
    public void inputChangeEvent( float t, boolean v ) {
        Simulator.schedule(
                new Simulator.Event( t + delay ) {
                    void trigger() {
                        outputChangeEvent( time, v );
                    }
                }
        );
    }

    /** Simulate an output change on this wire
     *  @param time tells when this wire's input changes
     *  @param v gives the new value on this wire
     *  Passes the new value to the input of the gate to which this wire goes.
     *  @see #inputChangeEvent(float, boolean)
     */
    private void outputChangeEvent( float time, boolean v ) {
        destination.inputChangeEvent( time, dstPin, v );
    }

} // class Wire

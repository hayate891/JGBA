package cpu;

public class CPSR {
	
	/*
	 * The mode bit combinations - define the processor's operating mode.
	 * 
	 * Not all combinations of the mode define a valid processor mode.
	 */
	public static final byte USER = 0x10; 
	public static final byte FIQ = 0x11;
	public static final byte IRQ = 0x12;
	public static final byte SUPERVISOR = 0x13;
	public static final byte ABORT = 0x17;
	public static final byte UNDEFINED = 0x1B;
	public static final byte SYSTEM = 0x1F;
	
	
	protected boolean negative; //Negative/Less Than - Bit 31
	protected boolean zero; //Zero - Bit 30
	protected boolean carry; //Carry/Borrow/Extend - Bit 29
	protected boolean overflow; //Overflow - Bit 28
	
	//Bit 27-8 are RESERVED
	
	protected boolean irqDisable; //IRQ Interrupt Disable - Bit 7
	protected boolean fiqDisable; //FIQ Interrupt Disable - Bit 6
	protected boolean thumb; //THUMB State (if false, ARM state) - Bit 5
	
	protected byte mode; //5 mode bits - Bit 4-0
	
	public CPSR() {
		//TODO Initialize this correctly
		mode = USER;
	}
	
	public void load(int cpsr) {
		negative = (cpsr & 0x80000000) == 0x80000000;
		zero = (cpsr & 0x40000000) == 0x40000000;
		carry = (cpsr & 0x20000000) == 0x20000000;
		overflow = (cpsr & 0x10000000) == 0x10000000;
		
		irqDisable = (cpsr & 0x80) == 0x80;
		fiqDisable = (cpsr & 0x40) == 0x40;
		thumb = (cpsr & 0x20) == 0x20;
		mode = (byte) (cpsr & 0x1F);
	}
	
	public int save() {
		int result = 0;
		if (negative)
			result |= 0x80000000;
		if (zero)
			result |= 0x40000000;
		if (carry)
			result |= 0x20000000;
		if (overflow)
			result |= 0x10000000;
		
		if (irqDisable)
			result |= 0x80;
		if (fiqDisable)
			result |= 0x40;
		if (thumb)
			result |= 0x20;
		result |= (mode & 0x1F);
		return result;
	}
}
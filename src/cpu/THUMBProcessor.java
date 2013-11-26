package cpu;

import static cpu.THUMBALUOpCode.*;
import utils.ByteUtils;

public class THUMBProcessor implements CPU.IProcessor {

	private final CPU cpu;

	public THUMBProcessor(CPU cpu) {
		this.cpu = cpu;
	}

	@Override
	public void execute(int pc) {
		/*2 Bytes stored in Little-Endian format
		  15-8, 7-0 */
		byte top = cpu.accessROM(pc+1), bot = cpu.accessROM(pc);

		byte bit15_to_11 = (byte)((top >>> 3) & 0x1F);

		//From 0x0 to 0x1F (0-31)
		switch(bit15_to_11) {
		case 0x0: lslImm(top, bot); break; 
		case 0x1: lsrImm(top, bot); break;
		case 0x2: asrImm(top, bot); break;
		case 0x3: /*Add or Sub*/
			if ((top & 0x2) == 0) /*Bit 9 CLEAR*/
				if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
					addReg(top, bot);
				else
					addImm3(top, bot);
			else
				if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
					subReg(top, bot);
				else
					subImm3(top, bot);
			break;
		case 0x4: movImm8(top, bot); break;
		case 0x5: cmpImm8(top, bot); break;
		case 0x6: addImm8(top, bot); break;
		case 0x7: subImm8(top, bot); break;
		case 0x8:
			if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
				aluOp(top, bot); 
			else
				hiRegOpsBranchX(top, bot);
			break;
		case 0x9: pcRelativeLoad(top, bot); break;
		case 0xA: /*Bit 11 CLEAR*/
			if ((top & 0x2) == 0)/*Bit 9 CLEAR, Store register offset*/
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - B*/
					str(top, bot);
				else
					strb(top, bot);
			else
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - S*/
					storeHW(top, bot);  //strh
				else
					loadSignExtendedByte(top, bot); //ldsb
			break;
		case 0xB: /*Bit 11 SET*/
			if ((top & 0x2) == 0)/*Bit 9 CLEAR, Load register offset*/
				if ((top & 0x4) == 0) /*Bit 10 CLEAR*/
					ldr(top, bot);
				else 
					ldrb(top, bot);
			else
				if ((top & 0x4) == 0) /*Bit 10 CLEAR - S*/
					loadHW(top, bot);  //ldrh
				else
					loadSignExtendedHW(top, bot); //ldsh
			break;
		case 0xC: strImm(top, bot); break;
		case 0xD: ldrImm(top, bot); break;
		case 0xE: strbImm(top, bot); break;
		case 0xF: ldrbImm(top, bot); break;
		case 0x10: storeHWImm(top, bot); break;
		case 0x11: loadHWImm(top, bot); break;
		case 0x12: spRelativeStore(top, bot); break;
		case 0x13: spRelativeLoad(top, bot); break;
		case 0x14: loadAddress(false, top, bot); break;
		case 0x15: loadAddress(true, top, bot); break;
		case 0x16:
			if ((top & 0x7) == 0) /*Bit 10-8 CLEAR*/
				addOffsetToSP(bot); 
			if ((top & 0x6) == 0x4) /*Bit 10 SET, Bit 9 CLEAR*/
				pushRegisters(top, bot); 
			//TODO Add undefined instruction trap here?
			break;
		case 0x17:
			if ((top & 0x6) == 0x4) /*Bit 10 SET, Bit 9 CLEAR*/
				popRegisters(top, bot); 
			//TODO Add undefined instruction trap here?
			break;
		case 0x18: storeMult(top, bot); break;
		case 0x19: loadMult(top, bot); break;
		case 0x1A: conditionalBranch(top, bot); break;
		case 0x1B:
			if ((top & 0xF) == 0xF)
				softwareInterrupt(pc, bot);
			else
				conditionalBranch(top, bot);
			break;
		case 0x1C: unconditionalBranch(top, bot);
		case 0x1D: System.out.println("UNDEFINED THUMB INSTRUCTION"); break; //Undefined????
		case 0x1E: longBranch(false, top, bot); break;
		case 0x1F: longBranch(true, top, bot); break;
		}
	}

	private void lslImm(byte top, byte bot) {
		//Lower 3 bits of top, top 2 bits of bot 
		byte offset5 = (byte) (((top & 0x7) << 2) | ((bot & 0xC0) >>> 6));
		int val = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		if (offset5 != 0) { //Carry not affected by 0
			//Carry set by the last bit shifted out (=sign of the value shifted one less)
			cpu.cpsr.carry = ((val << (offset5-1)) < 0);
			val <<= offset5;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		//The method will & 0x7 for us
		cpu.setLowReg(bot, val);
	}

	private void lsrImm(byte top, byte bot){
		//Lower 3 bits of top, top 2 bits of bot 
		byte offset5 = (byte) (((top & 0x7) << 2) | ((bot & 0xC0) >>> 6));
		int val = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		if (offset5 != 0) {
			//Carry set by the last bit shifted out (= 0 bit of the value shifted one less)
			cpu.cpsr.carry = (((val >>> (offset5 - 1)) & 0x1) == 0x1);
			val >>>= offset5;
		}
		else {
			//This is actually LSR #32 (page 13 of ARM pdf), thus carry = sign bit, value becomes 0
			cpu.cpsr.carry = (val < 0);
			val = 0;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		//The method will & 0x7 for us
		cpu.setLowReg(bot, val);
	}

	private void asrImm(byte top, byte bot) {
		//Lower 3 bits of top, top 2 bits of bot 
		byte offset5 = (byte) (((top & 0x7) << 2) | ((bot & 0xC0) >>> 6));
		int val = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		if (offset5 != 0) {
			//Carry set by the last bit shifted out (= 0 bit of the value shifted one less)
			cpu.cpsr.carry = (((val >> (offset5 - 1)) & 0x1) == 0x1);
			val >>= offset5;
		}
		else {
			//This is actually ASR #32 (page 13 of ARM pdf), thus carry = sign bit, value becomes either all 0's or all 1's
			cpu.cpsr.carry = (val < 0);
			val >>= 31;
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		//The method will & 0x7 for us
		cpu.setLowReg(bot, val);
	}

	private void addReg(byte top, byte bot) {
		//Last bit of top, first 2 bits of bot
		int arg = cpu.getLowReg((byte) (((top & 0x1) << 2) | ((bot & 0xC0) >>> 6)));
		int source = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		//The method will & 0x7 for us
		cpu.setLowReg(bot, cpu.setAddFlags(source, arg));
	}

	private void addImm3(byte top, byte bot) {
		//Last bit of top, first 2 bits of bot
		int arg = ((top & 0x1) << 2) | ((bot & 0xC0) >>> 6);
		int source = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		//The method will & 0x7 for us
		cpu.setLowReg(bot, cpu.setAddFlags(source, arg));
	}

	private void subReg(byte top, byte bot) {
		//Last bit of top, first 2 bits of bot
		int arg = cpu.getLowReg((byte) (((top & 0x1) << 2) | ((bot & 0xC0) >>> 6)));
		int source = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		//The method will & 0x7 for us
		cpu.setLowReg(bot, cpu.setSubFlags(source, arg));
	}

	private void subImm3(byte top, byte bot) {
		//Last bit of top, first 2 bits of bot
		int arg = ((top & 0x1) << 2) | ((bot & 0xC0) >>> 6);
		int source = cpu.getLowReg((byte) ((bot & 0x38) >>> 3));
		//The method will & 0x7 for us
		cpu.setLowReg(bot, cpu.setSubFlags(source, arg));
	}

	private void movImm8(byte top, byte offset8) {
		cpu.cpsr.negative = false;
		cpu.cpsr.zero = (offset8 == 0);
		//The method will & 0x7 for us, but we need to & 0xFF instead of implicit widening
		cpu.setLowReg(top, offset8 & 0xFF);
	}

	private void cmpImm8(byte top, byte offset8) {
		//The method will & 0x7 for us
		int val = cpu.getLowReg(top);
		//Need to & 0xFF instead of implicit widening
		cpu.setSubFlags(val, offset8 & 0xFF);
	}

	private void addImm8(byte top, byte offset8) {
		//The method will & 0x7 for us
		int val = cpu.getLowReg(top);
		//The method will & 0x7 for us, but we need to & 0xFF instead of implicit widening
		cpu.setLowReg(top, cpu.setAddFlags(val, offset8 & 0xFF));
	}

	private void subImm8(byte top, byte offset8) {
		//The method will & 0x7 for us
		int val = cpu.getLowReg(top);
		//The method will & 0x7 for us, but we need to & 0xFF instead of implicit widening
		cpu.setLowReg(top, cpu.setSubFlags(val, offset8 & 0xFF));
	}

	private void aluOp(byte top, byte bot) {
		//Op is the last 2 bits of top and the first two bits of bot
		byte op = (byte) (((top & 0x3) << 2) | ((bot & 0xC0) >>> 6));
		byte rs = (byte) ((bot & 0x38) >>> 3);
		byte rd = (byte) (bot & 0x7);
		switch(op) {
		case AND: and(rd, rs); break;
		case EOR: eor(rd, rs); break;
		case LSL: lsl(rd, rs); break;
		case LSR: lsr(rd, rs); break;
		case ASR: asr(rd, rs); break;
		case ADC: adc(rd, rs); break;
		case SBC: sbc(rd, rs); break;
		case ROR: ror(rd, rs); break;
		case TST: tst(rd, rs); break;
		case NEG: neg(rd, rs); break;
		case CMP: cmp(rd, rs); break;
		case CMN: cmn(rd, rs); break;
		case ORR: orr(rd, rs); break;
		case MUL: mul(rd, rs); break;
		case BIC: bic(rd, rs); break;
		case MVN: mvn(rd, rs); break;
		default: throw new RuntimeException("Should not occur! OP=" + ByteUtils.hex(op));
		}
	}

	/**
	 * AND Rd, Rs (Rd = Rd & Rs)
	 */
	private void and(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) & cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * EOR Rd, Rs (Rd = Rd ^ Rs)
	 */
	private void eor(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) ^ cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * LSL Rd, Rs (Rd = Rd << Rs)
	 */
	private void lsl(byte rd, byte rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) { //Carry not affected by 0 shift
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (val << (shift-1) < 0);
				val <<= shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is the 0 bit
				cpu.cpsr.carry = ((val & 0x1) == 0x1); 
				val = 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				val = 0;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * LSR Rd, Rs (Rd = Rd >>> Rs)
	 * LSR = Logical Shift Right
	 */
	private void lsr(byte rd, byte rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((val >>> (shift - 1)) & 0x1) == 0x1);
				val >>>= shift;
			}
			else if (shift == 32) { //We do this manually b/c in Java, shifts are % #bits, carry is sign bit
				cpu.cpsr.carry = (val < 0); 
				val = 0;
			}
			else { //Shift >32, 0's!
				cpu.cpsr.carry = false;
				val = 0;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * ASR Rd, Rs (Rd = Rd >> Rs)
	 * ASR = Arithmetic (signed) Shift Right
	 */
	private void asr(byte rd, byte rs) {
		int val = cpu.getLowReg(rd);
		int shift = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the shift
		if (shift > 0) {
			if (shift < 32) { //Shifts <32 are fine, carry is the last bit shifted out
				cpu.cpsr.carry = (((val >> (shift - 1)) & 0x1) == 0x1);
				val >>= shift;
			}
			else { //Shift >=32, carry is equal to the sign bit, value becomes either all 1's or 0's
				cpu.cpsr.carry = (val < 0);
				val >>= 31;
			}
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * ADC Rd, Rs (Rd = Rd + Rs + C-bit)
	 */
	private void adc(byte rd, byte rs) {
		cpu.setLowReg(rd, cpu.setAddCarryFlags(cpu.getLowReg(rd), cpu.getLowReg(rs)));
	}
	
	/**
	 * SBC Rd, Rs (Rd = Rd - Rs - NOT C-bit)
	 */
	private void sbc(byte rd, byte rs) {
		cpu.setLowReg(rd, cpu.setSubCarryFlags(cpu.getLowReg(rd), cpu.getLowReg(rs)));
	}
	
	/**
	 * ROR Rd, Rs (Rd = Rd ROR Rs)
	 * ROR = ROtate Right
	 */
	private void ror(byte rd, byte rs) {
		int val = cpu.getLowReg(rd);
		int rotate = cpu.getLowReg(rs) & 0xFF; //Only the least significant byte is used to determine the rotate
		if (rotate > 0) {
			rotate = rotate & 0x1F; //If rotate >32, we subtract 32 until in range [0-31] -> same as & 0x1F (31)
			if (rotate > 0) { //Carry is the last bit rotated out
				cpu.cpsr.carry = (((val >>> (rotate - 1)) & 0x1) == 0x1);
				//Val is the remaining bits from the shift and the removed bits shifted to the left
				val = (val >>> rotate) | (val << (32-rotate));
			}
			else //ROR 32, carry equal to sign bit
				cpu.cpsr.carry = (val < 0);
		}
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * TST Rd, Rs (Set condition codes on Rd & Rs)
	 */
	private void tst(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) & cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
	}
	
	/**
	 * NEG Rd, Rs (Rd = -Rs)
	 */
	private void neg(byte rd, byte rs) {
		int val = cpu.getLowReg(rs);
		//Because of the two's complement system, 0 will overflow back to 0
		//and Integer.MIN_VALUE will still be Integer.MIN_VALUE, which is marked as an overflow condition.
		//Therefore, this is identical to cpu.cpsr.overflow = (val == -val);
		cpu.cpsr.overflow = ((val ^ -val) == 0);
		val = -val;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * CMP Rd, Rs (Set condition codes on Rd - Rs)
	 */
	private void cmp(byte rd, byte rs) {
		cpu.setSubFlags(cpu.getLowReg(rd), cpu.getLowReg(rs));
	}
	
	/**
	 * CMN Rd, Rs (Set condition codes on Rd + Rs)
	 */
	private void cmn(byte rd, byte rs) {
		cpu.setAddFlags(cpu.getLowReg(rd), cpu.getLowReg(rs));
	}
	
	/**
	 * ORR Rd, Rs (Rd = Rd | Rs)
	 */
	private void orr(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) | cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * MUL Rd, Rs (Rd = Rd * Rs)
	 */
	private void mul(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) * cpu.getLowReg(rs);
		cpu.cpsr.carry = false;
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * BIC Rd, Rs (Rd = Rd AND NOT Rs)
	 */
	private void bic(byte rd, byte rs) {
		int val = cpu.getLowReg(rd) & ~cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}
	
	/**
	 * MVN Rd, Rs (Rd = NOT Rs)
	 */
	private void mvn(byte rd, byte rs) {
		int val = ~cpu.getLowReg(rs);
		cpu.cpsr.negative = (val < 0);
		cpu.cpsr.zero = (val == 0);
		cpu.setLowReg(rd, val);
	}

	private void hiRegOpsBranchX(byte top, byte bot) {
		
	}

	private void pcRelativeLoad(byte top, byte bot) {

	}

	private void str(byte top, byte bot) { 

	}

	private void strb(byte top, byte bot) {

	}

	private void ldr(byte top, byte bot) {

	}

	private void ldrb(byte top, byte bot) {

	}

	private void storeHW(byte top, byte bot) {

	}

	private void loadSignExtendedByte(byte top, byte bot) {

	}

	private void loadHW(byte top, byte bot) {

	}	

	private void loadSignExtendedHW(byte top, byte bot) {

	}

	private void strImm(byte top, byte bot) {

	}

	private void strbImm(byte top, byte bot) {

	}

	private void ldrImm(byte top, byte bot) {

	}

	private void ldrbImm(byte top, byte bot) {

	}

	private void storeHWImm(byte top, byte bot) {

	}

	private void loadHWImm(byte top, byte bot) {

	}

	private void spRelativeStore(byte top, byte bot) {

	}

	private void spRelativeLoad(byte top, byte bot) {

	}

	private void loadAddress(boolean sp, byte top, byte bot) {
		//If sp is false, we use pc instead
	}

	private void addOffsetToSP(byte bot) { 

	}

	private void pushRegisters(byte top, byte bot) {

	}

	private void popRegisters(byte top, byte bot) {

	}

	private void storeMult(byte top, byte bot) {

	}

	private void loadMult(byte top, byte bot) {

	}

	private void conditionalBranch(byte top, byte bot) {

	}

	private void softwareInterrupt(int pc, byte val) {

	}

	private void unconditionalBranch(byte top, byte bot) {

	}

	private void longBranch(boolean offsetLow, byte top, byte bot) {

	}

}

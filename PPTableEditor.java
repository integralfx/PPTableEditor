import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PPTableEditor
{
    public static void main(String[] args)
    {
        PPTableEditor ppte = new PPTableEditor("RxVega_M_soft_PowerTable.reg");
        ppte.save("RxVega_M_soft_PowerTable_modded.reg");
    }

    public PPTableEditor(String reg_file) throws IllegalArgumentException
    {
        if(!parse_reg_file(reg_file))
            throw new IllegalArgumentException("Invalid registry file");

        pplay = new ATOM_POWERPLAY_TABLE(pp_bytes);
        if(pplay.sHeader.usStructureSize != pp_bytes.length)
            throw new IllegalArgumentException("Invalid PowerPlay table");

        byte[] bytes = new byte[ATOM_POWERTUNE_TABLE.size];
        System.arraycopy(pp_bytes, pplay.usPowerTuneTableOffset, bytes, 0, bytes.length);
        ptune = new ATOM_POWERTUNE_TABLE(bytes);

        bytes = new byte[ATOM_MCLK_TABLE.size];
        System.arraycopy(pp_bytes, pplay.usMclkDependencyTableOffset, bytes, 0, bytes.length);
        ATOM_MCLK_TABLE mclk_table = new ATOM_MCLK_TABLE(bytes);
        mclk_entries = new ATOM_MCLK_ENTRY[mclk_table.ucNumEntries];
        for(int i = 0; i < mclk_entries.length; i++)
        {
            int offset = pplay.usMclkDependencyTableOffset + ATOM_MCLK_TABLE.size +
                            ATOM_MCLK_ENTRY.size * i;
            bytes = new byte[ATOM_MCLK_ENTRY.size];
            System.arraycopy(pp_bytes, offset, bytes, 0, bytes.length);
            mclk_entries[i] = new ATOM_MCLK_ENTRY(bytes);
        }

        bytes = new byte[ATOM_SCLK_ENTRY.size];
        System.arraycopy(pp_bytes, pplay.usSclkDependencyTableOffset, bytes, 0, bytes.length);
        ATOM_SCLK_TABLE sclk_table = new ATOM_SCLK_TABLE(bytes);
        sclk_entries = new ATOM_SCLK_ENTRY[sclk_table.ucNumEntries];
        for(int i = 0; i < sclk_entries.length; i++)
        {
            int offset = pplay.usSclkDependencyTableOffset + ATOM_SCLK_TABLE.size +
                            ATOM_SCLK_ENTRY.size * i;
            bytes = new byte[ATOM_SCLK_ENTRY.size];
            System.arraycopy(pp_bytes, offset, bytes, 0, bytes.length);
            sclk_entries[i] = new ATOM_SCLK_ENTRY(bytes);
        }

        bytes = new byte[ATOM_VOLTAGE_ENTRY.size];
        System.arraycopy(pp_bytes, pplay.usVddcLookupTableOffset, bytes, 0, bytes.length);
        ATOM_VOLTAGE_TABLE voltage_table = new ATOM_VOLTAGE_TABLE(bytes);
        voltage_entries = new ATOM_VOLTAGE_ENTRY[voltage_table.ucNumEntries];
        for(int i = 0; i < voltage_entries.length; i++)
        {
            int offset = pplay.usVddcLookupTableOffset + ATOM_VOLTAGE_TABLE.size +
                            ATOM_VOLTAGE_ENTRY.size * i;
            bytes = new byte[ATOM_VOLTAGE_ENTRY.size];
            System.arraycopy(pp_bytes, offset, bytes, 0, bytes.length);
            voltage_entries[i] = new ATOM_VOLTAGE_ENTRY(bytes);
        }
    }

    public boolean save(String filename)
    {
        // copy fields
        System.arraycopy(pplay.to_bytes(), 0, pp_bytes, 0, ATOM_POWERPLAY_TABLE.size);
        System.arraycopy(ptune.to_bytes(), 0, pp_bytes, pplay.usPowerTuneTableOffset, ATOM_POWERTUNE_TABLE.size);
        for(int i = 0; i < mclk_entries.length; i++)
        {
            int offset = pplay.usMclkDependencyTableOffset + ATOM_MCLK_TABLE.size +
                         ATOM_MCLK_ENTRY.size * i;
            System.arraycopy(mclk_entries[i].to_bytes(), 0, pp_bytes, offset, ATOM_MCLK_ENTRY.size);
        }
        for(int i = 0; i < sclk_entries.length; i++)
        {
            int offset = pplay.usSclkDependencyTableOffset + ATOM_SCLK_TABLE.size +
                         ATOM_SCLK_ENTRY.size * i;
            System.arraycopy(sclk_entries[i].to_bytes(), 0, pp_bytes, offset, ATOM_SCLK_ENTRY.size);
        }
        for(int i = 0; i < voltage_entries.length; i++)
        {
            int offset = pplay.usVddcLookupTableOffset + ATOM_VOLTAGE_TABLE.size +
                         ATOM_VOLTAGE_ENTRY.size * i;
            System.arraycopy(voltage_entries[i].to_bytes(), 0, pp_bytes, offset, ATOM_VOLTAGE_ENTRY.size);
        }

        Path path = Paths.get(filename);
        try
        {
            StringBuilder data = new StringBuilder();

            data.append(reg_header);

            for(int i = 1; i <= pp_bytes.length; i++)
            {
                data.append(String.format("%02X", pp_bytes[i - 1]));

                if(i != pp_bytes.length)
                    data.append(", ");

                if(i != pp_bytes.length && i % 16 == 0)
                    data.append("\\\r\n");
            }

            Files.write(path, data.toString().getBytes());
        }
        catch(IOException e)
        {
            System.err.println("Failed to write to " + filename);
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private boolean parse_reg_file(String reg_file)
    {
        try {
            String data = new String(Files.readAllBytes(Paths.get(reg_file))),
                   needle = "\"PP_PhmSoftPowerPlayTable\"=hex:";
            int start = data.indexOf(needle);
            if(start == -1) return false;

            reg_header = data.substring(0, start) + needle;

            start += needle.length();
            String hex = data.substring(start);

            String[] hex_bytes = hex.split(",|\\\\");
            ArrayList<Byte> bytes = new ArrayList<>();
            for(String s : hex_bytes)
            {
                s = s.trim().toUpperCase();

                if(s.matches("[0-9A-F]{2}$"))
                    bytes.add((byte)Integer.parseInt(s, 16));
            }

            pp_bytes = new byte[bytes.size()];
            for(int i = 0; i < pp_bytes.length; i++)
                pp_bytes[i] = bytes.get(i);

            return true;
        }
        catch(IOException e)
        {
            System.err.println("Failed to read " + reg_file);
            e.printStackTrace();
            return false;
        }
    }

    /*
     * converts the 2 bytes at offset to an unsigned 16 bit value
     * bytes are in little endian
     * returns an int as java doesn't have an unsigned 16 bit value
     */
    private int bytes_to_uint16(byte[] bytes, int offset)
    {
        return Byte.toUnsignedInt(bytes[offset + 1]) << 8 | Byte.toUnsignedInt(bytes[offset]);
    }

    private byte[] uint16_to_bytes(int n)
    {
        byte[] b = new byte[2];
        b[0] = (byte)(n & 0xFF);
        b[1] = (byte)((n >> 8) & 0xFF);
        return b;
    }

    private long bytes_to_uint32(byte[] bytes, int offset)
    {
        return Byte.toUnsignedLong(bytes[offset + 3]) << 24 |
               Byte.toUnsignedLong(bytes[offset + 2]) << 16 |
               Byte.toUnsignedLong(bytes[offset + 1]) << 8 |
               Byte.toUnsignedLong(bytes[offset]);
    }

    private byte[] uint32_to_bytes(long n)
    {
        byte[] b = new byte[4];
        b[0] = (byte)(n & 0xFF);
        b[1] = (byte)((n >> 8) & 0xFF);
        b[2] = (byte)((n >> 16) & 0xFF);
        b[3] = (byte)((n >> 24) & 0xFF);
        return b;
    }

    class ATOM_COMMON_TABLE_HEADER
    {
        public static final int size = 4;

        public final int usStructureSize;     // 2 bytes
        public final byte ucTableFormatRevision;
        public final byte ucTableContentRevision;

        public ATOM_COMMON_TABLE_HEADER(byte[] bytes) throws IllegalArgumentException
        {
            if(bytes.length != size)
            {
                throw new IllegalArgumentException(
                    String.format(
                        "ATOM_COMMON_TABLE_HEADER: expected %d bytes, got %d bytes", 
                        size, bytes.length
                    )
                );
            }

            usStructureSize = bytes_to_uint16(bytes, 0);
            ucTableFormatRevision = bytes[2];
            ucTableContentRevision = bytes[3];
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            System.arraycopy(uint16_to_bytes(usStructureSize), 0, b, i, 2); i += 2;
            b[i++] = ucTableFormatRevision;
            b[i++] = ucTableContentRevision;

            return b;
        }
    }

    class ATOM_POWERPLAY_TABLE
    {
        public static final int size = ATOM_COMMON_TABLE_HEADER.size + 73;

        public final ATOM_COMMON_TABLE_HEADER sHeader;
        public final byte ucTableRevision;
        public final int usTableSize;
        public long ulGoldenPPID;
        public long ulGoldenRevision;
        public int usFormatID;
        public int usVoltageTime;
        public long ulPlatformCaps;
        public long ulMaxODEngineClock;
        public long ulMaxODMemoryClock;
        public int usPowerControlLimit;
        public final int usUlvVoltageOffset;
        public final int usStateArrayOffset;
        public final int usFanTableOffset;
        public final int usThermalControllerOffset;
        public final int usReserv;
        public final int usMclkDependencyTableOffset;
        public final int usSclkDependencyTableOffset;
        public final int usVddcLookupTableOffset;
        public final int usVddgfxLookupTableOffset;
        public final int usMMDependencyTableOffset;
        public final int usVCEStateTableOffset;
        public final int usPPMTableOffset;
        public final int usPowerTuneTableOffset;
        public final int usHardLimitTableOffset;
        public final int usPCIETableOffset;
        public final int usGPIOTableOffset;
        public final int[] usReserved = new int[6];

        public ATOM_POWERPLAY_TABLE(byte[] bytes)
        {
            sHeader = new ATOM_COMMON_TABLE_HEADER(Arrays.copyOf(bytes, ATOM_COMMON_TABLE_HEADER.size));
            int i = ATOM_COMMON_TABLE_HEADER.size;
            ucTableRevision = bytes[i++];
            usTableSize = bytes_to_uint16(bytes, i); i += 2;
            ulGoldenPPID = bytes_to_uint32(bytes, i); i += 4;
            ulGoldenRevision = bytes_to_uint32(bytes, i); i += 4;
            usFormatID = bytes_to_uint16(bytes, i); i += 2;
            usVoltageTime = bytes_to_uint16(bytes, i); i += 2;
            ulPlatformCaps = bytes_to_uint32(bytes, i); i += 4;
            ulMaxODEngineClock = bytes_to_uint32(bytes, i); i += 4;
            ulMaxODMemoryClock = bytes_to_uint32(bytes, i); i += 4;
            usPowerControlLimit = bytes_to_uint16(bytes, i); i += 2;
            usUlvVoltageOffset = bytes_to_uint16(bytes, i); i += 2;
            usStateArrayOffset = bytes_to_uint16(bytes, i); i += 2;
            usFanTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usThermalControllerOffset = bytes_to_uint16(bytes, i); i += 2;
            usReserv = bytes_to_uint16(bytes, i); i += 2;
            usMclkDependencyTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usSclkDependencyTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usVddcLookupTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usVddgfxLookupTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usMMDependencyTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usVCEStateTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usPPMTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usPowerTuneTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usHardLimitTableOffset = bytes_to_uint16(bytes, i); i += 2;
            usPCIETableOffset = bytes_to_uint16(bytes, i); i += 2;
            usGPIOTableOffset = bytes_to_uint16(bytes, i); i += 2;
            for(int j = 0; j < 6; j++)
            {
                usReserved[j] = bytes_to_uint16(bytes, i); i+= 2;
            }
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            System.arraycopy(sHeader.to_bytes(), 0, b, i, ATOM_COMMON_TABLE_HEADER.size); 
            i += ATOM_COMMON_TABLE_HEADER.size;
            b[i++] = ucTableRevision;
            System.arraycopy(uint16_to_bytes(usTableSize), 0, b, i, 2); i += 2;
            System.arraycopy(uint32_to_bytes(ulGoldenPPID), 0, b, i, 4); i += 4;
            System.arraycopy(uint32_to_bytes(ulGoldenRevision), 0, b, i, 4); i += 4;
            System.arraycopy(uint16_to_bytes(usFormatID), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usVoltageTime), 0, b, i, 2); i += 2;
            System.arraycopy(uint32_to_bytes(ulPlatformCaps), 0, b, i, 4); i += 4;
            System.arraycopy(uint32_to_bytes(ulMaxODEngineClock), 0, b, i, 4); i += 4;
            System.arraycopy(uint32_to_bytes(ulMaxODMemoryClock), 0, b, i, 4); i += 4;
            System.arraycopy(uint16_to_bytes(usPowerControlLimit), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usUlvVoltageOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usStateArrayOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usFanTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usThermalControllerOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usReserv), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usMclkDependencyTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usSclkDependencyTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usVddcLookupTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usVddgfxLookupTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usMMDependencyTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usVCEStateTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usPPMTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usPowerTuneTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usHardLimitTableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usPCIETableOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usGPIOTableOffset), 0, b, i, 2); i += 2;
            for(int j = 0; j < 6; j++)
            {
                System.arraycopy(uint16_to_bytes(usReserved[j]), 0, b, i, 2); i += 2;
            }

            return b;
        }
    }

    class ATOM_MCLK_ENTRY
    {
        public static final int size = 13;

        public byte ucVddcInd;
        public int usVddci;
        public int usVddgfxOffset;
        public int usMvdd;
        public long ulMclk;
        public int usReserved;

        public ATOM_MCLK_ENTRY(byte[] bytes)
        {
            int i = 0;
            ucVddcInd = bytes[i++];
            usVddci = bytes_to_uint16(bytes, i); i += 2;
            usVddgfxOffset = bytes_to_uint16(bytes, i); i += 2;
            usMvdd = bytes_to_uint16(bytes, i); i += 2;
            ulMclk = bytes_to_uint32(bytes, i); i += 4;
            usReserved = bytes_to_uint16(bytes, i); i += 2;
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            b[i++] = ucVddcInd;
            System.arraycopy(uint16_to_bytes(usVddci), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usVddgfxOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usMvdd), 0, b, i, 2); i += 2;
            System.arraycopy(uint32_to_bytes(ulMclk), 0, b, i, 4); i += 4;
            System.arraycopy(uint16_to_bytes(usReserved), 0, b, i, 2); i += 2;

            return b;
        }
    }

    class ATOM_MCLK_TABLE
    {
        public static final int size = 2;

        public final byte ucRevId;
        public final byte ucNumEntries;

        public ATOM_MCLK_TABLE(byte[] bytes)
        {
            int i = 0;
            ucRevId = bytes[i++];
            ucNumEntries = bytes[i++];
        }
    }
    
    class ATOM_SCLK_ENTRY
    {
        public static final int size = 15;

        public byte ucVddInd;                   // index into voltage_entries
        public int usVddcOffset;
        public long ulSclk;
        public int usEdcCurrent;
        public byte ucReliabilityTemperature;
        public byte ucCKSVOffsetandDisable;
        public long ulSclkOffset; // Polaris Only, remove for compatibility with Fiji

        public ATOM_SCLK_ENTRY(byte[] bytes)
        {
            int i = 0;
            ucVddInd = bytes[i++];
            usVddcOffset = bytes_to_uint16(bytes, i); i += 2;
            ulSclk = bytes_to_uint32(bytes, i); i += 4;
            usEdcCurrent = bytes_to_uint16(bytes, i); i += 2;
            ucReliabilityTemperature = bytes[i++];
            ucCKSVOffsetandDisable = bytes[i++];
            ulSclkOffset = bytes_to_uint32(bytes, i); i += 4;
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            b[i++] = ucVddInd;
            System.arraycopy(uint16_to_bytes(usVddcOffset), 0, b, i, 2); i += 2;
            System.arraycopy(uint32_to_bytes(ulSclk), 0, b, i, 4); i += 4;
            System.arraycopy(uint16_to_bytes(usEdcCurrent), 0, b, i, 2); i += 2;
            b[i++] = ucReliabilityTemperature;
            b[i++] = ucCKSVOffsetandDisable;
            System.arraycopy(uint32_to_bytes(ulSclkOffset), 0, b, i, 4); i += 4;

            return b;
        }
    }

    class ATOM_SCLK_TABLE
    {
        public static final int size = 2;

        public final byte ucRevId;
        public final byte ucNumEntries;

        public ATOM_SCLK_TABLE(byte[] bytes)
        {
            int i = 0;
            ucRevId = bytes[i++];
            ucNumEntries = bytes[i++];
        }
    }

    class ATOM_VOLTAGE_ENTRY
    {
        public static final int size = 8;

        public int usVdd;
        public int usCACLow;
        public int usCACMid;
        public int usCACHigh;

        public ATOM_VOLTAGE_ENTRY(byte[] bytes)
        {
            int i = 0;
            usVdd = bytes_to_uint16(bytes, i); i += 2;
            usCACLow = bytes_to_uint16(bytes, i); i += 2;
            usCACMid = bytes_to_uint16(bytes, i); i += 2;
            usCACHigh = bytes_to_uint16(bytes, i); i += 2;
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            System.arraycopy(uint16_to_bytes(usVdd), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usCACLow), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usCACMid), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usCACHigh), 0, b, i, 2); i += 2;

            return b;
        }
    }

    class ATOM_VOLTAGE_TABLE
    {
        public static final int size = 2;

        public final byte ucRevId;
        public final byte ucNumEntries;

        public ATOM_VOLTAGE_TABLE(byte[] bytes)
        {
            int i = 0;
            ucRevId = bytes[i++];
            ucNumEntries = bytes[i++];
        }
    }

    class ATOM_POWERTUNE_TABLE
    {
        public static final int size = 48;

        public byte ucRevId;
        public int usTDP;
        public int usConfigurableTDP;
        public int usTDC;
        public int usBatteryPowerLimit;
        public int usSmallPowerLimit;
        public int usLowCACLeakage;
        public int usHighCACLeakage;
        public int usMaximumPowerDeliveryLimit;
        public int usTjMax;
        public int usPowerTuneDataSetID;
        public int usEDCLimit;
        public int usSoftwareShutdownTemp;
        public int usClockStretchAmount;
        public int usTemperatureLimitHotspot;
        public int usTemperatureLimitLiquid1;
        public int usTemperatureLimitLiquid2;
        public int usTemperatureLimitVrVddc;
        public int usTemperatureLimitVrMvdd;
        public int usTemperatureLimitPlx;
        public byte ucLiquid1_I2C_address;
        public byte ucLiquid2_I2C_address;
        public byte ucLiquid_I2C_Line;
        public byte ucVr_I2C_address;
        public byte ucVr_I2C_Line;
        public byte ucPlx_I2C_address;
        public byte ucPlx_I2C_Line;
        public int usReserved;

        public ATOM_POWERTUNE_TABLE(byte[] bytes)
        {
            int i = 0;
            ucRevId = bytes[i++];
            usTDP = bytes_to_uint16(bytes, i); i += 2;
            usConfigurableTDP = bytes_to_uint16(bytes, i); i += 2;
            usTDC = bytes_to_uint16(bytes, i); i += 2;
            usBatteryPowerLimit = bytes_to_uint16(bytes, i); i += 2;
            usSmallPowerLimit = bytes_to_uint16(bytes, i); i += 2;
            usLowCACLeakage = bytes_to_uint16(bytes, i); i += 2;
            usHighCACLeakage = bytes_to_uint16(bytes, i); i += 2;
            usMaximumPowerDeliveryLimit = bytes_to_uint16(bytes, i); i += 2;
            usTjMax = bytes_to_uint16(bytes, i); i += 2;
            usPowerTuneDataSetID = bytes_to_uint16(bytes, i); i += 2;
            usEDCLimit = bytes_to_uint16(bytes, i); i += 2;
            usSoftwareShutdownTemp = bytes_to_uint16(bytes, i); i += 2;
            usClockStretchAmount = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitHotspot = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitLiquid1 = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitLiquid2 = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitVrVddc = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitVrMvdd = bytes_to_uint16(bytes, i); i += 2;
            usTemperatureLimitPlx = bytes_to_uint16(bytes, i); i += 2;
            ucLiquid1_I2C_address = bytes[i++];
            ucLiquid2_I2C_address = bytes[i++];
            ucLiquid_I2C_Line = bytes[i++];
            ucVr_I2C_address = bytes[i++];
            ucVr_I2C_Line = bytes[i++];
            ucPlx_I2C_address = bytes[i++];
            ucPlx_I2C_Line = bytes[i++];
            usReserved = bytes_to_uint16(bytes, i); i += 2;
        }

        public byte[] to_bytes()
        {
            byte[] b = new byte[size];

            int i = 0;
            b[i++] = ucRevId;
            System.arraycopy(uint16_to_bytes(usTDP), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usConfigurableTDP), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTDC), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usBatteryPowerLimit), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usSmallPowerLimit), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usLowCACLeakage), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usHighCACLeakage), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usMaximumPowerDeliveryLimit), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTjMax), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usPowerTuneDataSetID), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usEDCLimit), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usSoftwareShutdownTemp), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usClockStretchAmount), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitHotspot), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitLiquid1), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitLiquid2), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitVrVddc), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitVrMvdd), 0, b, i, 2); i += 2;
            System.arraycopy(uint16_to_bytes(usTemperatureLimitPlx), 0, b, i, 2); i += 2;
            b[i++] = ucLiquid1_I2C_address;
            b[i++] = ucLiquid2_I2C_address;
            b[i++] = ucLiquid_I2C_Line;
            b[i++] = ucVr_I2C_address;
            b[i++] = ucVr_I2C_Line;
            b[i++] = ucPlx_I2C_address;
            b[i++] = ucPlx_I2C_Line;
            System.arraycopy(uint16_to_bytes(usReserved), 0, b, i, 2); i += 2;

            return b;
        }
    };

    private String reg_header;
    private byte[] pp_bytes;
    public ATOM_POWERPLAY_TABLE pplay;
    public ATOM_POWERTUNE_TABLE ptune;
    public final ATOM_MCLK_ENTRY[] mclk_entries;
    public final ATOM_SCLK_ENTRY[] sclk_entries;
    public final ATOM_VOLTAGE_ENTRY[] voltage_entries;
}
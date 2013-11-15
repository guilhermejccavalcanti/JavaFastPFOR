package me.lemire.integercompression.benchmarktools;

import me.lemire.integercompression.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This will run benchmarks using a set of posting lists stored as CSV files.
 * 
 * @author lemire
 * 
 */
public class BenchmarkCSV {
        static IntegratedIntegerCODEC codecs[] = {
                new IntegratedComposition(new IntegratedFastPFOR(),
                        new IntegratedVariableByte()),
                new IntegratedComposition(new IntegratedBinaryPacking(),
                        new IntegratedVariableByte()),
                new IntegratedVariableByte() };
        static IntegratedByteIntegerCODEC bcodecs[]= {
                new IntegratedVariableByte()
        };
        static IntegerCODEC regcodecs[] = {
                new Composition(new FastPFOR(),
                        new VariableByte()),
                new Composition(new BinaryPacking(),
                        new VariableByte()),
                new VariableByte() };
        static ByteIntegerCODEC regbcodecs[]= {
                new VariableByte()
        };
        
        
        public static ArrayList<int[]> loadIntegers(String filename,Format f) throws IOException {
                if(f== Format.ONEARRAYPERLINE) {
                ArrayList<int[]> answer = new ArrayList<int[]>();
                BufferedReader br = new BufferedReader(new FileReader(filename));
                String s;
                while((s = br.readLine())!= null) {
                        String[] numbers = s.split("[,;;]");
                        int[] a = new int[numbers.length];
                        for (int k = 0; k < numbers.length; ++k) {
                                a[k] = Integer.parseInt(numbers[k].trim());
                        }        
                        answer.add(a);
                }                
                return answer;
                } else {//Format.ONEARRAYPERFILE
                        ArrayList<Integer> answer = new ArrayList<Integer>();
                        BufferedReader br = new BufferedReader(new FileReader(filename));
                        String s;
                        while((s = br.readLine())!= null) {
                                String[] numbers = s.split("[,;;]");
                                for (int k = 0; k < numbers.length; ++k) {
                                        answer.add(Integer.parseInt(numbers[k].trim()));
                                }        
                        }            
                        int[] actualanswer = new int[answer.size()];
                        for(int i = 0; i < answer.size(); ++i)
                                actualanswer[i] = answer.get(i);
                        ArrayList<int[]> wrap = new ArrayList<int[]>();
                        wrap.add(actualanswer);
                        return wrap;        
                }
        }
        public enum Format {
                ONEARRAYPERLINE, ONEARRAYPERFILE
        }

        public enum CompressionMode {
                AS_IS, DELTA
        }
        public static void main(String[] args) throws IOException {
                Format myformat = Format.ONEARRAYPERLINE;
                CompressionMode cm = CompressionMode.DELTA;
                ArrayList<String> files = new ArrayList<String>();
                for(String s : args) {
                        if(s.startsWith("-")) {// it is a flag
                                if(s.equals("--onearrayperfile"))
                                        myformat = Format.ONEARRAYPERFILE;
                                if(s.equals("--nodelta"))
                                        cm = CompressionMode.AS_IS;
                                else throw new RuntimeException("I don't understand: "+s);
                        } else {// it is a filename
                                files.add(s);
                        }
                }
                if(myformat == Format.ONEARRAYPERFILE)
                        System.out.println("Treating each file as one array.");
                else if(myformat == Format.ONEARRAYPERLINE)
                        System.out.println("Each line of each file is an array: use --onearrayperfile to change.");
                if(cm == CompressionMode.AS_IS)
                        System.out.println("Compressing the integers 'as is' (no differential coding)");
                else 
                        System.out.println("Using differential coding (arrays will be sorted): use --nodelta to prevent sorting");
                ArrayList<int[]> data = new ArrayList<int[]>();
                for (int k = 0; k < args.length; ++k)
                        for(int[] x: loadIntegers(args[k],myformat))
                                data.add(x);
                System.out.println("Loaded " + data.size() + " array(s)");
                if(cm == CompressionMode.DELTA) {
                        System.out.println("Sorting the arrray(s) because you are using differential coding");
                        for(int[] x: data)
                                Arrays.sort(x);
                }
                bench(data, cm, false);
                bench(data, cm, false);
                bench(data, cm, true);
                bytebench(data, cm, false);
                bytebench(data, cm, false);
                bytebench(data, cm, true);
        }

        public static void bench(ArrayList<int[]> postings, CompressionMode cm, boolean verbose) {
                int maxlength = 0;
                for (int[] x : postings)
                        if (maxlength < x.length)
                                maxlength = x.length;
                if (verbose) System.out.println("Max array length: " + maxlength);
                int[] compbuffer = new int[2*maxlength + 1024];
                int[] decompbuffer = new int[maxlength];
                if(verbose)
                        System.out.println("Scheme -- bits/int -- speed (mis)");
                for (IntegerCODEC c : (cm == CompressionMode.DELTA ? codecs: regcodecs)) {
                        long bef = 0;
                        long aft = 0;
                        long decomptime = 0;
                        long volumein = 0;
                        long volumeout = 0;
                        int[][] compdata = new int[postings.size()][];
                        for (int k = 0; k < postings.size(); ++k) {
                                int[] in = postings.get(k);
                                IntWrapper inpos = new IntWrapper(0);
                                IntWrapper outpos = new IntWrapper(0);
                                c.compress(in, inpos, in.length, compbuffer,
                                        outpos);
                                int clength = outpos.get();
                                inpos = new IntWrapper(0);
                                outpos = new IntWrapper(0);
                                c.uncompress(compbuffer, inpos, clength,
                                        decompbuffer, outpos);
                                volumein += in.length;
                                volumeout += clength;

                                if (outpos.get() != in.length)
                                        throw new RuntimeException("bug");
                                for (int z = 0; z < in.length; ++z)
                                        if (in[z] != decompbuffer[z])
                                                throw new RuntimeException(
                                                        "bug");
                                compdata[k] = Arrays
                                        .copyOf(compbuffer, clength);
                        }
                        bef = System.nanoTime();
                        for (int[] cin : compdata) {
                                IntWrapper inpos = new IntWrapper(0);
                                IntWrapper outpos = new IntWrapper(0);
                                c.uncompress(cin, inpos, cin.length,
                                        decompbuffer, outpos);
                                if (inpos.get() != cin.length)
                                        throw new RuntimeException("bug");
                        }
                        aft = System.nanoTime();
                        decomptime += (aft - bef);
                        double bitsPerInt = volumeout * 32.0 / volumein;
                        double decompressSpeed = volumein * 1000.0
                                / (decomptime);
                        if (verbose)
                                System.out.println(c.toString()
                                        + "\t"
                                        + String.format("\t%1$.2f\t%2$.2f",
                                                bitsPerInt, decompressSpeed));

                }
        }

        public static void bytebench(ArrayList<int[]> postings, CompressionMode cm, boolean verbose) {
                int maxlength = 0;
                for (int[] x : postings)
                        if (maxlength < x.length)
                                maxlength = x.length;
                if (verbose) System.out.println("Max array length: " + maxlength);
                byte[] compbuffer = new byte[4*(maxlength + 1024)];
                int[] decompbuffer = new int[maxlength];
                if(verbose)
                        System.out.println("Scheme -- bits/int -- speed (mis)");
                for (ByteIntegerCODEC c : (cm == CompressionMode.DELTA ? bcodecs: regbcodecs)) {
                        long bef = 0;
                        long aft = 0;
                        long decomptime = 0;
                        long volumein = 0;
                        long volumeout = 0;
                        byte[][] compdata = new byte[postings.size()][];
                        for (int k = 0; k < postings.size(); ++k) {
                                int[] in = postings.get(k);
                                IntWrapper inpos = new IntWrapper(0);
                                IntWrapper outpos = new IntWrapper(0);
                                c.compress(in, inpos, in.length, compbuffer,
                                        outpos);
                                int clength = outpos.get();
                                inpos = new IntWrapper(0);
                                outpos = new IntWrapper(0);
                                c.uncompress(compbuffer, inpos, clength,
                                        decompbuffer, outpos);
                                volumein += in.length;
                                volumeout += clength;

                                if (outpos.get() != in.length)
                                        throw new RuntimeException("bug");
                                for (int z = 0; z < in.length; ++z)
                                        if (in[z] != decompbuffer[z])
                                                throw new RuntimeException(
                                                        "bug");
                                compdata[k] = Arrays
                                        .copyOf(compbuffer, clength);
                        }
                        bef = System.nanoTime();
                        for (byte[] cin : compdata) {
                                IntWrapper inpos = new IntWrapper(0);
                                IntWrapper outpos = new IntWrapper(0);
                                c.uncompress(cin, inpos, cin.length,
                                        decompbuffer, outpos);
                                if (inpos.get() != cin.length)
                                        throw new RuntimeException("bug");
                        }
                        aft = System.nanoTime();
                        decomptime += (aft - bef);
                        double bitsPerInt = volumeout * 8.0 / volumein;
                        double decompressSpeed = volumein * 1000.0
                                / (decomptime);
                        if (verbose)
                                System.out.println(c.toString()
                                        + "\t"
                                        + String.format("\t%1$.2f\t%2$.2f",
                                                bitsPerInt, decompressSpeed));

                }
        }

}

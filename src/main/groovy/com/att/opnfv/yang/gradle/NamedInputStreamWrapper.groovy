package com.att.opnfv.yang.gradle

import org.opendaylight.yangtools.yang.parser.util.NamedInputStream

import java.io.IOException;
import java.io.InputStream

/** This is like NamedFileInputStream, but it takes an existing InputStream and makes it a NamedInputStream
 * by wrapping the original InputStream.
 */
class NamedInputStreamWrapper  extends InputStream implements NamedInputStream {
	private InputStream	inputStream
	private String		name
	
	public NamedInputStreamWrapper(InputStream inputStream, String name) {
		this.inputStream	= inputStream
		this.name			= name	
	}
	
	public int read() throws IOException { return inputStream.read() }
	public int read(byte[] b) throws IOException { return inputStream.read(b) }
	public int read(byte[] b, int off, int len) throws IOException { return inputStream.read(b, off, len) }
	public long skip(long n) throws IOException { return inputStream.skip(n) }
	public int available() throws IOException { return inputStream.available() }
	public void close() throws IOException { inputStream.close() }
	public synchronized void mark(int readlimit) { inputStream.mark(readlimit) }
	public synchronized void reset() throws IOException { inputStream.reset() }
	public boolean markSupported() { return inputStream.markSupported() }
		
	/** The NamedInputStream protocol requires the toString() method return the name value. */
	public String toString() { return name }
}

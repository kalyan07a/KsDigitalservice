package com.pdf.printer.dto;


public class FileInfo {
    private String b_fileName;
    private String c_fileName;
    private String url;
    private int pageCount;
    //private String printType;
	public String getB_fileName() {
		return b_fileName;
	}
	public void setB_fileName(String b_fileName) {
		this.b_fileName = b_fileName;
	}
	public String getC_fileName() {
		return c_fileName;
	}
	public void setC_fileName(String c_fileName) {
		this.c_fileName = c_fileName;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public int getPageCount() {
		return pageCount;
	}
	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}
	public FileInfo(String b_fileName, String c_fileName, String url, int pageCount) {
		super();
		this.b_fileName = b_fileName;
		this.c_fileName = c_fileName;
		this.url = url;
		this.pageCount = pageCount;
	}
	
	public FileInfo() {
		super();
	}
	@Override
	public String toString() {
		return "FileInfo [b_fileName=" + b_fileName + ", c_fileName=" + c_fileName + ", url=" + url + ", pageCount="
				+ pageCount + "]";
	}

    // Constructor, Getters and Setters

    // Add getters and setters
	
    
}
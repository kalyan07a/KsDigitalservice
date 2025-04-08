package com.pdf.printer.dto;

// Assuming FileInfo looks something like this (adjust if needed)
public class FileInfo {
    private String b_fileName; // B&W filename (potentially custom)
    private String c_fileName; // Color filename (potentially custom)
    private String url;        // URL for B&W preview/download
    private String c_url;      // URL for Color preview/download
    private int pageCount;     // Final page count (original or custom)

    public FileInfo(String b_fileName, String c_fileName, String url, String c_url, int pageCount) {
        this.b_fileName = b_fileName;
        this.c_fileName = c_fileName;
        this.url = url;         // Base URL for B&W
        this.c_url = c_url;     // Base URL for Color
        this.pageCount = pageCount;
    }

    // Getters (and potentially Setters if not immutable)
    public String getB_fileName() { return b_fileName; }
    public String getC_fileName() { return c_fileName; }
    public String getUrl() { return url; }
    public String getC_url() { return c_url; }
    public int getPageCount() { return pageCount; }

    @Override
    public String toString() {
        return "FileInfo{" +
               "b_fileName='" + b_fileName + '\'' +
               ", c_fileName='" + c_fileName + '\'' +
               ", url='" + url + '\'' +
               ", c_url='" + c_url + '\'' +
               ", pageCount=" + pageCount +
               '}';
    }
}
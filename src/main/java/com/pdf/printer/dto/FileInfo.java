package com.pdf.printer.dto;

public class FileInfo {
    private String b_fileName; // B&W filename (potentially custom)
    private String c_fileName; // Color filename (potentially custom)
    private String url;        // URL for B&W preview/download (can be null)
    private String c_url;      // URL for Color preview/download
    private int pageCount;     // Final page count (original or custom)
    private String originalFileName; // Added for display/reference
    private String mimeType; // Added to store original mime type

    // Constructor updated
    public FileInfo(String b_fileName, String c_fileName, String url, String c_url, int pageCount, String originalFileName, String mimeType) {
        this.b_fileName = b_fileName;
        this.c_fileName = c_fileName;
        this.url = url;
        this.c_url = c_url;
        this.pageCount = pageCount;
        this.originalFileName = originalFileName;
        this.mimeType = mimeType;
    }

    // Getters
    public String getB_fileName() { return b_fileName; }
    public String getC_fileName() { return c_fileName; }
    public String getUrl() { return url; }
    public String getC_url() { return c_url; }
    public int getPageCount() { return pageCount; }
    public String getOriginalFileName() { return originalFileName; }
    public String getMimeType() { return mimeType; } // Getter for mime type


    @Override
    public String toString() {
        return "FileInfo{" +
               "b_fileName='" + b_fileName + '\'' +
               ", c_fileName='" + c_fileName + '\'' +
               ", url='" + url + '\'' +
               ", c_url='" + c_url + '\'' +
               ", pageCount=" + pageCount +
               ", originalFileName='" + originalFileName + '\'' +
               ", mimeType='" + mimeType + '\'' +
               '}';
    }
}
package com.pdf.printer.dto;

// Using Lombok for boilerplate code (optional)
// import lombok.Data;
// import lombok.ToString;

// @Data // Generates getters, setters, toString, equals, hashCode
// @ToString // Included in @Data, but explicit if needed
public class PaymentInitiationRequest {

    private String fileName;
    private int pageCount;
    private int printType;
    private int numberOfCopies; // Name MUST match JSON key from frontend

    // --- Standard Getters and Setters (Required if not using Lombok) ---

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getPrintType() {
        return printType;
    }

    public void setPrintType(int printType) {
        this.printType = printType;
    }

    public int getNumberOfCopies() {
        return numberOfCopies;
    }

    public void setNumberOfCopies(int numberOfCopies) {
        this.numberOfCopies = numberOfCopies;
    }

    // --- toString() method for logging (Recommended) ---
    @Override
    public String toString() {
        return "PaymentInitiationRequest{" +
                "fileName='" + fileName + '\'' +
                ", pageCount=" + pageCount +
                ", printType=" + printType +
                ", numberOfCopies=" + numberOfCopies +
                '}';
    }
}
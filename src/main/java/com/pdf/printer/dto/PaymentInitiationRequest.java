/*
 * package com.pdf.printer.dto;
 * 
 * // Using Lombok for boilerplate code, ensure Lombok dependency is in pom.xml
 * import lombok.Data; import lombok.NoArgsConstructor; import
 * lombok.AllArgsConstructor;
 * 
 * @Data // Generates getters, setters, toString, equals, hashCode
 * 
 * @NoArgsConstructor
 * 
 * @AllArgsConstructor public class PaymentInitiationRequest { private String
 * fileName; private int pageCount; private int printType; // 0 for B&W, 1 for
 * Color }
 */

package com.pdf.printer.dto;

// Ensure this DTO matches the JSON sent from the frontend for payment initiation
public class PaymentInitiationRequest {
    private String fileName; // This should be the FINAL filename (original or custom)
    private int pageCount;   // This should be the FINAL page count
    private int printType;   // 0 for B&W, 1 for Color

    // Constructors, Getters, Setters (or use Lombok)

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public int getPrintType() { return printType; }
    public void setPrintType(int printType) { this.printType = printType; }

     @Override
    public String toString() {
        return "PaymentInitiationRequest{" +
               "fileName='" + fileName + '\'' +
               ", pageCount=" + pageCount +
               ", printType=" + printType +
               '}';
    }
}
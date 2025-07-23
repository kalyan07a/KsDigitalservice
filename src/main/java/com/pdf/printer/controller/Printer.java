package com.pdf.printer.controller;

public enum Printer {
    PRINTER_0("-1", "sampath.k@print.epsonconnect.com", "Sampath", "9502884420", 2, 9, 10, 10, 5, 3, 15, 9, 5),
    PRINTER_1("1", "sampath.k@print.epsonconnect.com", "KS DIGITAL SERVICES", "7893845696", 2, 9, 10, 10, 5, 3, 15, 9, 5);

    private final String id;
    private final String email;
    private final String name;
    private final String phone;
    private final int range1;
    private final int range2;
    private final int range3;
    private final int b_cost_range1;
    private final int b_cost_range2;
    private final int b_cost_range3;
    private final int c_cost_range1;
    private final int c_cost_range2;
    private final int c_cost_range3;

    private Printer(String id, String email, String name, String phone, int range1, int range2, int range3,
                    int b_cost_range1, int b_cost_range2, int b_cost_range3,
                    int c_cost_range1, int c_cost_range2, int c_cost_range3) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.range1 = range1;
        this.range2 = range2;
        this.range3 = range3;
        this.b_cost_range1 = b_cost_range1;
        this.b_cost_range2 = b_cost_range2;
        this.b_cost_range3 = b_cost_range3;
        this.c_cost_range1 = c_cost_range1;
        this.c_cost_range2 = c_cost_range2;
        this.c_cost_range3 = c_cost_range3;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public int getRange1() {
        return range1;
    }

    public int getRange2() {
        return range2;
    }

    public int getRange3() {
        return range3;
    }

    public int getB_cost_range1() {
        return b_cost_range1;
    }

    public int getB_cost_range2() {
        return b_cost_range2;
    }

    public int getB_cost_range3() {
        return b_cost_range3;
    }

    public int getC_cost_range1() {
        return c_cost_range1;
    }

    public int getC_cost_range2() {
        return c_cost_range2;
    }

    public int getC_cost_range3() {
        return c_cost_range3;
    }

    // Static method to get email by printer ID
    public static String getEmailById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getEmail();
            }
        }
        return null; // or throw an exception if preferred
    }

    // Static method to get name by printer ID
    public static String getNameById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getName();
            }
        }
        return null;
    }

    // Static method to get phone by printer ID
    public static String getPhoneById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getPhone();
            }
        }
        return null;
    }

    // Static method to get name by phone number
    public static String getNameByPhone(String phoneNumber) {
        for (Printer printer : Printer.values()) {
            if (printer.getPhone().equals(phoneNumber)) {
                return printer.getName();
            }
        }
        return null;
    }

    // added for custom price
    public static int getRange1ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getRange1();
            }
        }
        return 0;
    }

    public static int getRange2ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getRange2();
            }
        }
        return 0;
    }

    public static int getRange3ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getRange3();
            }
        }
        return 0;
    }

    public static int getB_Cost_Range1ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getB_cost_range1();
            }
        }
        return 0;
    }

    public static int getB_Cost_Range2ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getB_cost_range2();
            }
        }
        return 0;
    }

    public static int getB_Cost_Range3ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getB_cost_range3();
            }
        }
        return 0;
    }

    public static int getc_Cost_Range1ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getC_cost_range1();
            }
        }
        return 0;
    }

    public static int getc_Cost_Range2ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getC_cost_range2();
            }
        }
        return 0;
    }

    public static int getc_Cost_Range3ById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return printer.getC_cost_range3();
            }
        }
        return 0;
    }

    // New nested class to hold multiple details at once 
    public static class PrinterDetails {
        private final int range1;
        private final int range2;
        private final int b_cost_range1;
        private final int b_cost_range2;
        private final int b_cost_range3;
        private final int c_cost_range1;
        private final int c_cost_range2;
        private final int c_cost_range3;
        private final String name;
        

      

        public PrinterDetails(int range1, int range2, int b_cost_range1, int b_cost_range2, int b_cost_range3,
				int c_cost_range1, int c_cost_range2, int c_cost_range3,String name) {
			super();
			this.range1 = range1;
			this.range2 = range2;
			this.b_cost_range1 = b_cost_range1;
			this.b_cost_range2 = b_cost_range2;
			this.b_cost_range3 = b_cost_range3;
			this.c_cost_range1 = c_cost_range1;
			this.c_cost_range2 = c_cost_range2;
			this.c_cost_range3 = c_cost_range3;
			this.name=name;
		}

		public int getRange1() {
            return range1;
        }

        public int getRange2() {
            return range2;
        }

        public int getB_cost_range1() {
            return b_cost_range1;
        }

        public int getB_cost_range2() {
            return b_cost_range2;
        }

        public int getB_cost_range3() {
            return b_cost_range3;
        }

		public int getC_cost_range1() {
			return c_cost_range1;
		}

		public int getC_cost_range2() {
			return c_cost_range2;
		}

		public int getC_cost_range3() {
			return c_cost_range3;
		}
		public String getName() {
			return name;
		}
        
    }
    
    

    // Static method to get PrinterDetails by id
    public static PrinterDetails getDetailsById(String i) {
        for (Printer printer : Printer.values()) {
            if (printer.getId().equals(i)) {
                return new PrinterDetails(
                        printer.getRange1(),
                        printer.getRange2(),
                        printer.getB_cost_range1(),
                        printer.getB_cost_range2(),
                        printer.getB_cost_range3(),
                		printer.getC_cost_range1(),
                		printer.getC_cost_range2(),
                		printer.getC_cost_range3(),
                		printer.getName());
            }
        }
        return null;
    }
}


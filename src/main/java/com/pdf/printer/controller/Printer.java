package com.pdf.printer.controller;

public enum Printer {
	PRINTER_0("-1", "ksdigital@print.epsonconnect.com","Sampath","9502884420"),
    PRINTER_1("1", "ksdigital@print.epsonconnect.com","Sampath","7893845696"),
    PRINTER_2("2", "email2@example.com","","");

    private final String id;
    private final String email;
    private final String name;
    private final String phone;

    Printer(String id, String email,String name,String phone) {
        this.id = id;
        this.email = email;
        this.name=name;
        this.phone=phone;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
    public String getName()
    {
    	return name;
    }
    public String getPhone()
    {
    	return phone;
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
}
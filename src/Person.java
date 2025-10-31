public record Person(Integer Id, String FirstName, String LastName, String Address, String PhoneNumber, Integer Age) {

    public Integer getId() {
        return Id;
    }

    public String getFirstName() {
        return FirstName;
    }

    public String getLastName() {
        return LastName;
    }

    public String getAddress() {
        return Address;
    }

    public String getPhoneNumber() {
        return PhoneNumber;
    }

    public Integer getAge() {
        return Age;
    }
}
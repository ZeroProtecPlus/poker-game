package model;

public class User {
    private final String userName;
    private int numbChips;

    public User(String userName) {
        this.userName = userName;
        this.numbChips = 10000;
    }


    public int getNumbChips() {
        return numbChips;
    }

    public void setNumbChips(int numbChips) {
        this.numbChips = numbChips;
    }
}

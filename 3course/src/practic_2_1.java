import java.util.Scanner;


public class practic_2_1 {
    private int m;
    private int n;
    public practic_2_1(int m, int n) {
        if (n <= 0){
            throw new IllegalArgumentException();
        }
        this.m = m;
        this.n = n;
        reduce();
    }
    public practic_2_1(){

        this(1, 1);
    }
    public practic_2_1(practic_2_1 other){
        m = other.m;
        n = other.n;
    }

    public static int gcd(int a, int b){
        while (b > 0){
            int c = b;
            b = a % b;
            a = c;
        }
        return a;
    }

    private void reduce(){
        int d = gcd(Math.abs(m), n);
        m /= d;
        n /= d;

    }

    @Override
    public String toString(){
        return m + "/" + n;
    }

    public boolean equals(practic_2_1 other){
        return m== other.m && n==other.n;
    }

    public practic_2_1 add(practic_2_1 other){
        int m = this.m * other.n + other.m * this.n;
        int n = this.n * other.n;
        return new practic_2_1(m, n);
    }

    public static practic_2_1 sum(practic_2_1[] array){
        practic_2_1 c = new practic_2_1();
        for (practic_2_1 e : array)
            c = c.add(e);
        return c;
    }

    public static practic_2_1 random(practic_2_1 other){
        int m = (int) (Math.random() * 50 - 100);
        int n = (int) (Math.random() * 50 + 1);
        return new practic_2_1(m, n);
    }
}

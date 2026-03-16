abstract class instruments{
    abstract void play();
}

class guitar extends instruments{
    void play(){
        System.out.println("Playing guitar");
    }
}
class piano extends instruments{
    void play(){
        System.out.println("Playing piano");
    }
}
class flute extends instruments{
    void play(){
        System.out.println("Playing flute");
    }
}

public class a {  
    public static void main(String[] args) {
        instruments[] inst = new instruments[10];
        for(int i=0;i<10;i++){
            int random = (int)(Math.random()*3);
            switch(random){
                case 0:
                    inst[i] = new guitar();
                    break;
                case 1:
                    inst[i] = new piano();
                    break;
                case 2:
                    inst[i] = new flute();
                    break;
            }
            inst[i].play();
        }
    }
}

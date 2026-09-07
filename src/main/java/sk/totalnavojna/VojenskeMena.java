package sk.totalnavojna;

import net.minecraft.util.RandomSource;

public class VojenskeMena {

    private static final String[] MENA = {
            "Serž. Šrapnel", "Des. Špekáčik", "Voj. Guláš", "Kpt. Kalach",
            "Slob. Bodák", "Čat. Sekera", "Voj. Haluska", "Des. Granát",
            "Serž. Klobása", "Voj. Bunker", "Rtm. Mína", "Slob. Pancier",
            "Voj. Kotlík", "Des. Rambo", "Serž. Slanina", "Voj. Šturmák",
            "Kpr. Zákop", "Voj. Borovička", "Des. Kasáreň", "Serž. Muška",
            "Voj. Kompót", "Slob. Ešus", "Des. Maskáč", "Voj. Patrón",
            "Serž. Oceľ", "Voj. Cibuľa", "Kpr. Ostreľka", "Des. Dynamit",
            "Voj. Kanón", "Slob. Šrot", "Serž. Baganča", "Voj. Konzerva",
            "Des. Perimeter", "Voj. Žiletka", "Serž. Tlakovka", "Slob. Kaliber",
            "Voj. Medveď", "Des. Šalát", "Kpr. Poplach", "Voj. Cvičák",
            "Serž. Rota", "Voj. Kufor", "Des. Blesk", "Slob. Manéver",
            "Voj. Cieľnik", "Serž. Ťažkáč", "Des. Odstrel", "Voj. Finta"
    };

    public static String nahodneMeno(RandomSource random) {
        return MENA[random.nextInt(MENA.length)];
    }
}

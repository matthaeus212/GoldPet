import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFFFF7E6),
      body: Center(
        child: Stack(
          alignment: Alignment.center,
          children: [
            SvgPicture.asset(
              'assets/images/splash/logo_background.svg',
              width: 64,
              height: 64,
            ),
            Image.asset(
              'assets/images/splash/logo_foreground.png',
              width: 64,
              height: 64,
            ),
          ],
        ),
      ),
    );
  }
}

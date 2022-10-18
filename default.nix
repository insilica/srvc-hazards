{ sources ? import ./nix/sources.nix, pkgs ? import sources.nixpkgs { } }:
with pkgs;
let
  # https://rgoswami.me/posts/nix-r-devtools/
  biobricks-R = rPackages.buildRPackage {
    name = "biobricks-R";
    src = fetchFromGitHub {
      owner = "biobricks-ai";
      repo = "biobricks-R";
      rev = "7c3f55b01b531e330009c6fe8c06aff46de793b7";
      sha256 = "wS6kvZH4tWLipad8pJdn+u2eMPJtSYNS59vwo9hzihI=";
    };
    propagatedBuildInputs = with rPackages; [ dplyr fs ];
  };
  rEnv = rWrapper.override {
    packages = with rPackages; [ biobricks-R withr ];
  };
in mkShell {
  buildInputs = [
    babashka
    clojure
    jdk
    niv
    nixfmt
    rEnv
    rlwrap
    srvc
  ];

  # Set bblib env var
  bblib = "bblib";
}

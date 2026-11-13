enum AppFlavor { play, izzy, libre, baidu }

extension ExtraAppFlavor on AppFlavor {
  bool get canEnableErrorReporting {
    switch (this) {
      case .play:
        return true;
      case .izzy:
      case .libre:
      case .baidu:
        return false;
    }
  }

  bool get hasMapStyleDefault {
    switch (this) {
      case .play:
      case .baidu:
        return true;
      case .izzy:
      case .libre:
        return false;
    }
  }
}

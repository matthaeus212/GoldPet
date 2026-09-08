// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'walk_models.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
  'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models',
);

WalkSpotDto _$WalkSpotDtoFromJson(Map<String, dynamic> json) {
  return _WalkSpotDto.fromJson(json);
}

/// @nodoc
mixin _$WalkSpotDto {
  double get latitude => throw _privateConstructorUsedError;
  double get longitude => throw _privateConstructorUsedError;
  String get type =>
      throw _privateConstructorUsedError; // PEE, POOP, PHOTO, etc.
  DateTime get timestamp => throw _privateConstructorUsedError;
  String? get imageUrl => throw _privateConstructorUsedError;
  String? get note => throw _privateConstructorUsedError;

  /// Serializes this WalkSpotDto to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of WalkSpotDto
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $WalkSpotDtoCopyWith<WalkSpotDto> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $WalkSpotDtoCopyWith<$Res> {
  factory $WalkSpotDtoCopyWith(
    WalkSpotDto value,
    $Res Function(WalkSpotDto) then,
  ) = _$WalkSpotDtoCopyWithImpl<$Res, WalkSpotDto>;
  @useResult
  $Res call({
    double latitude,
    double longitude,
    String type,
    DateTime timestamp,
    String? imageUrl,
    String? note,
  });
}

/// @nodoc
class _$WalkSpotDtoCopyWithImpl<$Res, $Val extends WalkSpotDto>
    implements $WalkSpotDtoCopyWith<$Res> {
  _$WalkSpotDtoCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of WalkSpotDto
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? latitude = null,
    Object? longitude = null,
    Object? type = null,
    Object? timestamp = null,
    Object? imageUrl = freezed,
    Object? note = freezed,
  }) {
    return _then(
      _value.copyWith(
            latitude: null == latitude
                ? _value.latitude
                : latitude // ignore: cast_nullable_to_non_nullable
                      as double,
            longitude: null == longitude
                ? _value.longitude
                : longitude // ignore: cast_nullable_to_non_nullable
                      as double,
            type: null == type
                ? _value.type
                : type // ignore: cast_nullable_to_non_nullable
                      as String,
            timestamp: null == timestamp
                ? _value.timestamp
                : timestamp // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            imageUrl: freezed == imageUrl
                ? _value.imageUrl
                : imageUrl // ignore: cast_nullable_to_non_nullable
                      as String?,
            note: freezed == note
                ? _value.note
                : note // ignore: cast_nullable_to_non_nullable
                      as String?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$WalkSpotDtoImplCopyWith<$Res>
    implements $WalkSpotDtoCopyWith<$Res> {
  factory _$$WalkSpotDtoImplCopyWith(
    _$WalkSpotDtoImpl value,
    $Res Function(_$WalkSpotDtoImpl) then,
  ) = __$$WalkSpotDtoImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    double latitude,
    double longitude,
    String type,
    DateTime timestamp,
    String? imageUrl,
    String? note,
  });
}

/// @nodoc
class __$$WalkSpotDtoImplCopyWithImpl<$Res>
    extends _$WalkSpotDtoCopyWithImpl<$Res, _$WalkSpotDtoImpl>
    implements _$$WalkSpotDtoImplCopyWith<$Res> {
  __$$WalkSpotDtoImplCopyWithImpl(
    _$WalkSpotDtoImpl _value,
    $Res Function(_$WalkSpotDtoImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of WalkSpotDto
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? latitude = null,
    Object? longitude = null,
    Object? type = null,
    Object? timestamp = null,
    Object? imageUrl = freezed,
    Object? note = freezed,
  }) {
    return _then(
      _$WalkSpotDtoImpl(
        latitude: null == latitude
            ? _value.latitude
            : latitude // ignore: cast_nullable_to_non_nullable
                  as double,
        longitude: null == longitude
            ? _value.longitude
            : longitude // ignore: cast_nullable_to_non_nullable
                  as double,
        type: null == type
            ? _value.type
            : type // ignore: cast_nullable_to_non_nullable
                  as String,
        timestamp: null == timestamp
            ? _value.timestamp
            : timestamp // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        imageUrl: freezed == imageUrl
            ? _value.imageUrl
            : imageUrl // ignore: cast_nullable_to_non_nullable
                  as String?,
        note: freezed == note
            ? _value.note
            : note // ignore: cast_nullable_to_non_nullable
                  as String?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$WalkSpotDtoImpl implements _WalkSpotDto {
  const _$WalkSpotDtoImpl({
    required this.latitude,
    required this.longitude,
    required this.type,
    required this.timestamp,
    this.imageUrl,
    this.note,
  });

  factory _$WalkSpotDtoImpl.fromJson(Map<String, dynamic> json) =>
      _$$WalkSpotDtoImplFromJson(json);

  @override
  final double latitude;
  @override
  final double longitude;
  @override
  final String type;
  // PEE, POOP, PHOTO, etc.
  @override
  final DateTime timestamp;
  @override
  final String? imageUrl;
  @override
  final String? note;

  @override
  String toString() {
    return 'WalkSpotDto(latitude: $latitude, longitude: $longitude, type: $type, timestamp: $timestamp, imageUrl: $imageUrl, note: $note)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$WalkSpotDtoImpl &&
            (identical(other.latitude, latitude) ||
                other.latitude == latitude) &&
            (identical(other.longitude, longitude) ||
                other.longitude == longitude) &&
            (identical(other.type, type) || other.type == type) &&
            (identical(other.timestamp, timestamp) ||
                other.timestamp == timestamp) &&
            (identical(other.imageUrl, imageUrl) ||
                other.imageUrl == imageUrl) &&
            (identical(other.note, note) || other.note == note));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    latitude,
    longitude,
    type,
    timestamp,
    imageUrl,
    note,
  );

  /// Create a copy of WalkSpotDto
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$WalkSpotDtoImplCopyWith<_$WalkSpotDtoImpl> get copyWith =>
      __$$WalkSpotDtoImplCopyWithImpl<_$WalkSpotDtoImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$WalkSpotDtoImplToJson(this);
  }
}

abstract class _WalkSpotDto implements WalkSpotDto {
  const factory _WalkSpotDto({
    required final double latitude,
    required final double longitude,
    required final String type,
    required final DateTime timestamp,
    final String? imageUrl,
    final String? note,
  }) = _$WalkSpotDtoImpl;

  factory _WalkSpotDto.fromJson(Map<String, dynamic> json) =
      _$WalkSpotDtoImpl.fromJson;

  @override
  double get latitude;
  @override
  double get longitude;
  @override
  String get type; // PEE, POOP, PHOTO, etc.
  @override
  DateTime get timestamp;
  @override
  String? get imageUrl;
  @override
  String? get note;

  /// Create a copy of WalkSpotDto
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$WalkSpotDtoImplCopyWith<_$WalkSpotDtoImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

WalkResponse _$WalkResponseFromJson(Map<String, dynamic> json) {
  return _WalkResponse.fromJson(json);
}

/// @nodoc
mixin _$WalkResponse {
  int get id => throw _privateConstructorUsedError;
  int get userId => throw _privateConstructorUsedError;
  DateTime get startTime => throw _privateConstructorUsedError;
  DateTime get endTime => throw _privateConstructorUsedError;
  double get distanceKm => throw _privateConstructorUsedError;
  int get durationSeconds => throw _privateConstructorUsedError;
  List<List<double>> get path => throw _privateConstructorUsedError;
  List<WalkSpotDto> get spots => throw _privateConstructorUsedError;
  double? get caloriesBurned => throw _privateConstructorUsedError;
  String? get notes => throw _privateConstructorUsedError;
  String? get startAddress => throw _privateConstructorUsedError;
  double? get startLatitude => throw _privateConstructorUsedError;
  double? get startLongitude => throw _privateConstructorUsedError;
  double? get endLatitude => throw _privateConstructorUsedError;
  double? get endLongitude => throw _privateConstructorUsedError;
  List<String> get petNames => throw _privateConstructorUsedError;
  List<int> get petIds => throw _privateConstructorUsedError;

  /// Serializes this WalkResponse to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of WalkResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $WalkResponseCopyWith<WalkResponse> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $WalkResponseCopyWith<$Res> {
  factory $WalkResponseCopyWith(
    WalkResponse value,
    $Res Function(WalkResponse) then,
  ) = _$WalkResponseCopyWithImpl<$Res, WalkResponse>;
  @useResult
  $Res call({
    int id,
    int userId,
    DateTime startTime,
    DateTime endTime,
    double distanceKm,
    int durationSeconds,
    List<List<double>> path,
    List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    String? startAddress,
    double? startLatitude,
    double? startLongitude,
    double? endLatitude,
    double? endLongitude,
    List<String> petNames,
    List<int> petIds,
  });
}

/// @nodoc
class _$WalkResponseCopyWithImpl<$Res, $Val extends WalkResponse>
    implements $WalkResponseCopyWith<$Res> {
  _$WalkResponseCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of WalkResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? userId = null,
    Object? startTime = null,
    Object? endTime = null,
    Object? distanceKm = null,
    Object? durationSeconds = null,
    Object? path = null,
    Object? spots = null,
    Object? caloriesBurned = freezed,
    Object? notes = freezed,
    Object? startAddress = freezed,
    Object? startLatitude = freezed,
    Object? startLongitude = freezed,
    Object? endLatitude = freezed,
    Object? endLongitude = freezed,
    Object? petNames = null,
    Object? petIds = null,
  }) {
    return _then(
      _value.copyWith(
            id: null == id
                ? _value.id
                : id // ignore: cast_nullable_to_non_nullable
                      as int,
            userId: null == userId
                ? _value.userId
                : userId // ignore: cast_nullable_to_non_nullable
                      as int,
            startTime: null == startTime
                ? _value.startTime
                : startTime // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            endTime: null == endTime
                ? _value.endTime
                : endTime // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            distanceKm: null == distanceKm
                ? _value.distanceKm
                : distanceKm // ignore: cast_nullable_to_non_nullable
                      as double,
            durationSeconds: null == durationSeconds
                ? _value.durationSeconds
                : durationSeconds // ignore: cast_nullable_to_non_nullable
                      as int,
            path: null == path
                ? _value.path
                : path // ignore: cast_nullable_to_non_nullable
                      as List<List<double>>,
            spots: null == spots
                ? _value.spots
                : spots // ignore: cast_nullable_to_non_nullable
                      as List<WalkSpotDto>,
            caloriesBurned: freezed == caloriesBurned
                ? _value.caloriesBurned
                : caloriesBurned // ignore: cast_nullable_to_non_nullable
                      as double?,
            notes: freezed == notes
                ? _value.notes
                : notes // ignore: cast_nullable_to_non_nullable
                      as String?,
            startAddress: freezed == startAddress
                ? _value.startAddress
                : startAddress // ignore: cast_nullable_to_non_nullable
                      as String?,
            startLatitude: freezed == startLatitude
                ? _value.startLatitude
                : startLatitude // ignore: cast_nullable_to_non_nullable
                      as double?,
            startLongitude: freezed == startLongitude
                ? _value.startLongitude
                : startLongitude // ignore: cast_nullable_to_non_nullable
                      as double?,
            endLatitude: freezed == endLatitude
                ? _value.endLatitude
                : endLatitude // ignore: cast_nullable_to_non_nullable
                      as double?,
            endLongitude: freezed == endLongitude
                ? _value.endLongitude
                : endLongitude // ignore: cast_nullable_to_non_nullable
                      as double?,
            petNames: null == petNames
                ? _value.petNames
                : petNames // ignore: cast_nullable_to_non_nullable
                      as List<String>,
            petIds: null == petIds
                ? _value.petIds
                : petIds // ignore: cast_nullable_to_non_nullable
                      as List<int>,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$WalkResponseImplCopyWith<$Res>
    implements $WalkResponseCopyWith<$Res> {
  factory _$$WalkResponseImplCopyWith(
    _$WalkResponseImpl value,
    $Res Function(_$WalkResponseImpl) then,
  ) = __$$WalkResponseImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    int id,
    int userId,
    DateTime startTime,
    DateTime endTime,
    double distanceKm,
    int durationSeconds,
    List<List<double>> path,
    List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    String? startAddress,
    double? startLatitude,
    double? startLongitude,
    double? endLatitude,
    double? endLongitude,
    List<String> petNames,
    List<int> petIds,
  });
}

/// @nodoc
class __$$WalkResponseImplCopyWithImpl<$Res>
    extends _$WalkResponseCopyWithImpl<$Res, _$WalkResponseImpl>
    implements _$$WalkResponseImplCopyWith<$Res> {
  __$$WalkResponseImplCopyWithImpl(
    _$WalkResponseImpl _value,
    $Res Function(_$WalkResponseImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of WalkResponse
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? id = null,
    Object? userId = null,
    Object? startTime = null,
    Object? endTime = null,
    Object? distanceKm = null,
    Object? durationSeconds = null,
    Object? path = null,
    Object? spots = null,
    Object? caloriesBurned = freezed,
    Object? notes = freezed,
    Object? startAddress = freezed,
    Object? startLatitude = freezed,
    Object? startLongitude = freezed,
    Object? endLatitude = freezed,
    Object? endLongitude = freezed,
    Object? petNames = null,
    Object? petIds = null,
  }) {
    return _then(
      _$WalkResponseImpl(
        id: null == id
            ? _value.id
            : id // ignore: cast_nullable_to_non_nullable
                  as int,
        userId: null == userId
            ? _value.userId
            : userId // ignore: cast_nullable_to_non_nullable
                  as int,
        startTime: null == startTime
            ? _value.startTime
            : startTime // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        endTime: null == endTime
            ? _value.endTime
            : endTime // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        distanceKm: null == distanceKm
            ? _value.distanceKm
            : distanceKm // ignore: cast_nullable_to_non_nullable
                  as double,
        durationSeconds: null == durationSeconds
            ? _value.durationSeconds
            : durationSeconds // ignore: cast_nullable_to_non_nullable
                  as int,
        path: null == path
            ? _value._path
            : path // ignore: cast_nullable_to_non_nullable
                  as List<List<double>>,
        spots: null == spots
            ? _value._spots
            : spots // ignore: cast_nullable_to_non_nullable
                  as List<WalkSpotDto>,
        caloriesBurned: freezed == caloriesBurned
            ? _value.caloriesBurned
            : caloriesBurned // ignore: cast_nullable_to_non_nullable
                  as double?,
        notes: freezed == notes
            ? _value.notes
            : notes // ignore: cast_nullable_to_non_nullable
                  as String?,
        startAddress: freezed == startAddress
            ? _value.startAddress
            : startAddress // ignore: cast_nullable_to_non_nullable
                  as String?,
        startLatitude: freezed == startLatitude
            ? _value.startLatitude
            : startLatitude // ignore: cast_nullable_to_non_nullable
                  as double?,
        startLongitude: freezed == startLongitude
            ? _value.startLongitude
            : startLongitude // ignore: cast_nullable_to_non_nullable
                  as double?,
        endLatitude: freezed == endLatitude
            ? _value.endLatitude
            : endLatitude // ignore: cast_nullable_to_non_nullable
                  as double?,
        endLongitude: freezed == endLongitude
            ? _value.endLongitude
            : endLongitude // ignore: cast_nullable_to_non_nullable
                  as double?,
        petNames: null == petNames
            ? _value._petNames
            : petNames // ignore: cast_nullable_to_non_nullable
                  as List<String>,
        petIds: null == petIds
            ? _value._petIds
            : petIds // ignore: cast_nullable_to_non_nullable
                  as List<int>,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$WalkResponseImpl implements _WalkResponse {
  const _$WalkResponseImpl({
    required this.id,
    required this.userId,
    required this.startTime,
    required this.endTime,
    required this.distanceKm,
    required this.durationSeconds,
    required final List<List<double>> path,
    required final List<WalkSpotDto> spots,
    this.caloriesBurned,
    this.notes,
    this.startAddress,
    this.startLatitude,
    this.startLongitude,
    this.endLatitude,
    this.endLongitude,
    final List<String> petNames = const [],
    final List<int> petIds = const [],
  }) : _path = path,
       _spots = spots,
       _petNames = petNames,
       _petIds = petIds;

  factory _$WalkResponseImpl.fromJson(Map<String, dynamic> json) =>
      _$$WalkResponseImplFromJson(json);

  @override
  final int id;
  @override
  final int userId;
  @override
  final DateTime startTime;
  @override
  final DateTime endTime;
  @override
  final double distanceKm;
  @override
  final int durationSeconds;
  final List<List<double>> _path;
  @override
  List<List<double>> get path {
    if (_path is EqualUnmodifiableListView) return _path;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_path);
  }

  final List<WalkSpotDto> _spots;
  @override
  List<WalkSpotDto> get spots {
    if (_spots is EqualUnmodifiableListView) return _spots;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_spots);
  }

  @override
  final double? caloriesBurned;
  @override
  final String? notes;
  @override
  final String? startAddress;
  @override
  final double? startLatitude;
  @override
  final double? startLongitude;
  @override
  final double? endLatitude;
  @override
  final double? endLongitude;
  final List<String> _petNames;
  @override
  @JsonKey()
  List<String> get petNames {
    if (_petNames is EqualUnmodifiableListView) return _petNames;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_petNames);
  }

  final List<int> _petIds;
  @override
  @JsonKey()
  List<int> get petIds {
    if (_petIds is EqualUnmodifiableListView) return _petIds;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_petIds);
  }

  @override
  String toString() {
    return 'WalkResponse(id: $id, userId: $userId, startTime: $startTime, endTime: $endTime, distanceKm: $distanceKm, durationSeconds: $durationSeconds, path: $path, spots: $spots, caloriesBurned: $caloriesBurned, notes: $notes, startAddress: $startAddress, startLatitude: $startLatitude, startLongitude: $startLongitude, endLatitude: $endLatitude, endLongitude: $endLongitude, petNames: $petNames, petIds: $petIds)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$WalkResponseImpl &&
            (identical(other.id, id) || other.id == id) &&
            (identical(other.userId, userId) || other.userId == userId) &&
            (identical(other.startTime, startTime) ||
                other.startTime == startTime) &&
            (identical(other.endTime, endTime) || other.endTime == endTime) &&
            (identical(other.distanceKm, distanceKm) ||
                other.distanceKm == distanceKm) &&
            (identical(other.durationSeconds, durationSeconds) ||
                other.durationSeconds == durationSeconds) &&
            const DeepCollectionEquality().equals(other._path, _path) &&
            const DeepCollectionEquality().equals(other._spots, _spots) &&
            (identical(other.caloriesBurned, caloriesBurned) ||
                other.caloriesBurned == caloriesBurned) &&
            (identical(other.notes, notes) || other.notes == notes) &&
            (identical(other.startAddress, startAddress) ||
                other.startAddress == startAddress) &&
            (identical(other.startLatitude, startLatitude) ||
                other.startLatitude == startLatitude) &&
            (identical(other.startLongitude, startLongitude) ||
                other.startLongitude == startLongitude) &&
            (identical(other.endLatitude, endLatitude) ||
                other.endLatitude == endLatitude) &&
            (identical(other.endLongitude, endLongitude) ||
                other.endLongitude == endLongitude) &&
            const DeepCollectionEquality().equals(other._petNames, _petNames) &&
            const DeepCollectionEquality().equals(other._petIds, _petIds));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    id,
    userId,
    startTime,
    endTime,
    distanceKm,
    durationSeconds,
    const DeepCollectionEquality().hash(_path),
    const DeepCollectionEquality().hash(_spots),
    caloriesBurned,
    notes,
    startAddress,
    startLatitude,
    startLongitude,
    endLatitude,
    endLongitude,
    const DeepCollectionEquality().hash(_petNames),
    const DeepCollectionEquality().hash(_petIds),
  );

  /// Create a copy of WalkResponse
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$WalkResponseImplCopyWith<_$WalkResponseImpl> get copyWith =>
      __$$WalkResponseImplCopyWithImpl<_$WalkResponseImpl>(this, _$identity);

  @override
  Map<String, dynamic> toJson() {
    return _$$WalkResponseImplToJson(this);
  }
}

abstract class _WalkResponse implements WalkResponse {
  const factory _WalkResponse({
    required final int id,
    required final int userId,
    required final DateTime startTime,
    required final DateTime endTime,
    required final double distanceKm,
    required final int durationSeconds,
    required final List<List<double>> path,
    required final List<WalkSpotDto> spots,
    final double? caloriesBurned,
    final String? notes,
    final String? startAddress,
    final double? startLatitude,
    final double? startLongitude,
    final double? endLatitude,
    final double? endLongitude,
    final List<String> petNames,
    final List<int> petIds,
  }) = _$WalkResponseImpl;

  factory _WalkResponse.fromJson(Map<String, dynamic> json) =
      _$WalkResponseImpl.fromJson;

  @override
  int get id;
  @override
  int get userId;
  @override
  DateTime get startTime;
  @override
  DateTime get endTime;
  @override
  double get distanceKm;
  @override
  int get durationSeconds;
  @override
  List<List<double>> get path;
  @override
  List<WalkSpotDto> get spots;
  @override
  double? get caloriesBurned;
  @override
  String? get notes;
  @override
  String? get startAddress;
  @override
  double? get startLatitude;
  @override
  double? get startLongitude;
  @override
  double? get endLatitude;
  @override
  double? get endLongitude;
  @override
  List<String> get petNames;
  @override
  List<int> get petIds;

  /// Create a copy of WalkResponse
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$WalkResponseImplCopyWith<_$WalkResponseImpl> get copyWith =>
      throw _privateConstructorUsedError;
}

CreateWalkRequest _$CreateWalkRequestFromJson(Map<String, dynamic> json) {
  return _CreateWalkRequest.fromJson(json);
}

/// @nodoc
mixin _$CreateWalkRequest {
  DateTime get startTime => throw _privateConstructorUsedError;
  DateTime get endTime => throw _privateConstructorUsedError;
  double get distanceKm => throw _privateConstructorUsedError;
  int get durationSeconds => throw _privateConstructorUsedError;
  List<List<double>> get path =>
      throw _privateConstructorUsedError; // [[lat, lon], [lat, lon]]
  List<WalkSpotDto> get spots => throw _privateConstructorUsedError;
  double? get caloriesBurned => throw _privateConstructorUsedError;
  String? get notes => throw _privateConstructorUsedError;
  List<int> get petIds => throw _privateConstructorUsedError;
  bool get isPublic => throw _privateConstructorUsedError;
  int? get followedCourseId => throw _privateConstructorUsedError;

  /// Serializes this CreateWalkRequest to a JSON map.
  Map<String, dynamic> toJson() => throw _privateConstructorUsedError;

  /// Create a copy of CreateWalkRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $CreateWalkRequestCopyWith<CreateWalkRequest> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $CreateWalkRequestCopyWith<$Res> {
  factory $CreateWalkRequestCopyWith(
    CreateWalkRequest value,
    $Res Function(CreateWalkRequest) then,
  ) = _$CreateWalkRequestCopyWithImpl<$Res, CreateWalkRequest>;
  @useResult
  $Res call({
    DateTime startTime,
    DateTime endTime,
    double distanceKm,
    int durationSeconds,
    List<List<double>> path,
    List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    List<int> petIds,
    bool isPublic,
    int? followedCourseId,
  });
}

/// @nodoc
class _$CreateWalkRequestCopyWithImpl<$Res, $Val extends CreateWalkRequest>
    implements $CreateWalkRequestCopyWith<$Res> {
  _$CreateWalkRequestCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of CreateWalkRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? startTime = null,
    Object? endTime = null,
    Object? distanceKm = null,
    Object? durationSeconds = null,
    Object? path = null,
    Object? spots = null,
    Object? caloriesBurned = freezed,
    Object? notes = freezed,
    Object? petIds = null,
    Object? isPublic = null,
    Object? followedCourseId = freezed,
  }) {
    return _then(
      _value.copyWith(
            startTime: null == startTime
                ? _value.startTime
                : startTime // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            endTime: null == endTime
                ? _value.endTime
                : endTime // ignore: cast_nullable_to_non_nullable
                      as DateTime,
            distanceKm: null == distanceKm
                ? _value.distanceKm
                : distanceKm // ignore: cast_nullable_to_non_nullable
                      as double,
            durationSeconds: null == durationSeconds
                ? _value.durationSeconds
                : durationSeconds // ignore: cast_nullable_to_non_nullable
                      as int,
            path: null == path
                ? _value.path
                : path // ignore: cast_nullable_to_non_nullable
                      as List<List<double>>,
            spots: null == spots
                ? _value.spots
                : spots // ignore: cast_nullable_to_non_nullable
                      as List<WalkSpotDto>,
            caloriesBurned: freezed == caloriesBurned
                ? _value.caloriesBurned
                : caloriesBurned // ignore: cast_nullable_to_non_nullable
                      as double?,
            notes: freezed == notes
                ? _value.notes
                : notes // ignore: cast_nullable_to_non_nullable
                      as String?,
            petIds: null == petIds
                ? _value.petIds
                : petIds // ignore: cast_nullable_to_non_nullable
                      as List<int>,
            isPublic: null == isPublic
                ? _value.isPublic
                : isPublic // ignore: cast_nullable_to_non_nullable
                      as bool,
            followedCourseId: freezed == followedCourseId
                ? _value.followedCourseId
                : followedCourseId // ignore: cast_nullable_to_non_nullable
                      as int?,
          )
          as $Val,
    );
  }
}

/// @nodoc
abstract class _$$CreateWalkRequestImplCopyWith<$Res>
    implements $CreateWalkRequestCopyWith<$Res> {
  factory _$$CreateWalkRequestImplCopyWith(
    _$CreateWalkRequestImpl value,
    $Res Function(_$CreateWalkRequestImpl) then,
  ) = __$$CreateWalkRequestImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({
    DateTime startTime,
    DateTime endTime,
    double distanceKm,
    int durationSeconds,
    List<List<double>> path,
    List<WalkSpotDto> spots,
    double? caloriesBurned,
    String? notes,
    List<int> petIds,
    bool isPublic,
    int? followedCourseId,
  });
}

/// @nodoc
class __$$CreateWalkRequestImplCopyWithImpl<$Res>
    extends _$CreateWalkRequestCopyWithImpl<$Res, _$CreateWalkRequestImpl>
    implements _$$CreateWalkRequestImplCopyWith<$Res> {
  __$$CreateWalkRequestImplCopyWithImpl(
    _$CreateWalkRequestImpl _value,
    $Res Function(_$CreateWalkRequestImpl) _then,
  ) : super(_value, _then);

  /// Create a copy of CreateWalkRequest
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? startTime = null,
    Object? endTime = null,
    Object? distanceKm = null,
    Object? durationSeconds = null,
    Object? path = null,
    Object? spots = null,
    Object? caloriesBurned = freezed,
    Object? notes = freezed,
    Object? petIds = null,
    Object? isPublic = null,
    Object? followedCourseId = freezed,
  }) {
    return _then(
      _$CreateWalkRequestImpl(
        startTime: null == startTime
            ? _value.startTime
            : startTime // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        endTime: null == endTime
            ? _value.endTime
            : endTime // ignore: cast_nullable_to_non_nullable
                  as DateTime,
        distanceKm: null == distanceKm
            ? _value.distanceKm
            : distanceKm // ignore: cast_nullable_to_non_nullable
                  as double,
        durationSeconds: null == durationSeconds
            ? _value.durationSeconds
            : durationSeconds // ignore: cast_nullable_to_non_nullable
                  as int,
        path: null == path
            ? _value._path
            : path // ignore: cast_nullable_to_non_nullable
                  as List<List<double>>,
        spots: null == spots
            ? _value._spots
            : spots // ignore: cast_nullable_to_non_nullable
                  as List<WalkSpotDto>,
        caloriesBurned: freezed == caloriesBurned
            ? _value.caloriesBurned
            : caloriesBurned // ignore: cast_nullable_to_non_nullable
                  as double?,
        notes: freezed == notes
            ? _value.notes
            : notes // ignore: cast_nullable_to_non_nullable
                  as String?,
        petIds: null == petIds
            ? _value._petIds
            : petIds // ignore: cast_nullable_to_non_nullable
                  as List<int>,
        isPublic: null == isPublic
            ? _value.isPublic
            : isPublic // ignore: cast_nullable_to_non_nullable
                  as bool,
        followedCourseId: freezed == followedCourseId
            ? _value.followedCourseId
            : followedCourseId // ignore: cast_nullable_to_non_nullable
                  as int?,
      ),
    );
  }
}

/// @nodoc
@JsonSerializable()
class _$CreateWalkRequestImpl implements _CreateWalkRequest {
  const _$CreateWalkRequestImpl({
    required this.startTime,
    required this.endTime,
    required this.distanceKm,
    required this.durationSeconds,
    required final List<List<double>> path,
    final List<WalkSpotDto> spots = const [],
    this.caloriesBurned,
    this.notes,
    final List<int> petIds = const [],
    this.isPublic = true,
    this.followedCourseId,
  }) : _path = path,
       _spots = spots,
       _petIds = petIds;

  factory _$CreateWalkRequestImpl.fromJson(Map<String, dynamic> json) =>
      _$$CreateWalkRequestImplFromJson(json);

  @override
  final DateTime startTime;
  @override
  final DateTime endTime;
  @override
  final double distanceKm;
  @override
  final int durationSeconds;
  final List<List<double>> _path;
  @override
  List<List<double>> get path {
    if (_path is EqualUnmodifiableListView) return _path;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_path);
  }

  // [[lat, lon], [lat, lon]]
  final List<WalkSpotDto> _spots;
  // [[lat, lon], [lat, lon]]
  @override
  @JsonKey()
  List<WalkSpotDto> get spots {
    if (_spots is EqualUnmodifiableListView) return _spots;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_spots);
  }

  @override
  final double? caloriesBurned;
  @override
  final String? notes;
  final List<int> _petIds;
  @override
  @JsonKey()
  List<int> get petIds {
    if (_petIds is EqualUnmodifiableListView) return _petIds;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_petIds);
  }

  @override
  @JsonKey()
  final bool isPublic;
  @override
  final int? followedCourseId;

  @override
  String toString() {
    return 'CreateWalkRequest(startTime: $startTime, endTime: $endTime, distanceKm: $distanceKm, durationSeconds: $durationSeconds, path: $path, spots: $spots, caloriesBurned: $caloriesBurned, notes: $notes, petIds: $petIds, isPublic: $isPublic, followedCourseId: $followedCourseId)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$CreateWalkRequestImpl &&
            (identical(other.startTime, startTime) ||
                other.startTime == startTime) &&
            (identical(other.endTime, endTime) || other.endTime == endTime) &&
            (identical(other.distanceKm, distanceKm) ||
                other.distanceKm == distanceKm) &&
            (identical(other.durationSeconds, durationSeconds) ||
                other.durationSeconds == durationSeconds) &&
            const DeepCollectionEquality().equals(other._path, _path) &&
            const DeepCollectionEquality().equals(other._spots, _spots) &&
            (identical(other.caloriesBurned, caloriesBurned) ||
                other.caloriesBurned == caloriesBurned) &&
            (identical(other.notes, notes) || other.notes == notes) &&
            const DeepCollectionEquality().equals(other._petIds, _petIds) &&
            (identical(other.isPublic, isPublic) ||
                other.isPublic == isPublic) &&
            (identical(other.followedCourseId, followedCourseId) ||
                other.followedCourseId == followedCourseId));
  }

  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  int get hashCode => Object.hash(
    runtimeType,
    startTime,
    endTime,
    distanceKm,
    durationSeconds,
    const DeepCollectionEquality().hash(_path),
    const DeepCollectionEquality().hash(_spots),
    caloriesBurned,
    notes,
    const DeepCollectionEquality().hash(_petIds),
    isPublic,
    followedCourseId,
  );

  /// Create a copy of CreateWalkRequest
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$CreateWalkRequestImplCopyWith<_$CreateWalkRequestImpl> get copyWith =>
      __$$CreateWalkRequestImplCopyWithImpl<_$CreateWalkRequestImpl>(
        this,
        _$identity,
      );

  @override
  Map<String, dynamic> toJson() {
    return _$$CreateWalkRequestImplToJson(this);
  }
}

abstract class _CreateWalkRequest implements CreateWalkRequest {
  const factory _CreateWalkRequest({
    required final DateTime startTime,
    required final DateTime endTime,
    required final double distanceKm,
    required final int durationSeconds,
    required final List<List<double>> path,
    final List<WalkSpotDto> spots,
    final double? caloriesBurned,
    final String? notes,
    final List<int> petIds,
    final bool isPublic,
    final int? followedCourseId,
  }) = _$CreateWalkRequestImpl;

  factory _CreateWalkRequest.fromJson(Map<String, dynamic> json) =
      _$CreateWalkRequestImpl.fromJson;

  @override
  DateTime get startTime;
  @override
  DateTime get endTime;
  @override
  double get distanceKm;
  @override
  int get durationSeconds;
  @override
  List<List<double>> get path; // [[lat, lon], [lat, lon]]
  @override
  List<WalkSpotDto> get spots;
  @override
  double? get caloriesBurned;
  @override
  String? get notes;
  @override
  List<int> get petIds;
  @override
  bool get isPublic;
  @override
  int? get followedCourseId;

  /// Create a copy of CreateWalkRequest
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$CreateWalkRequestImplCopyWith<_$CreateWalkRequestImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
